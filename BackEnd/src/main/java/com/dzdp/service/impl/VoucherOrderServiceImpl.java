package com.dzdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.dzdp.config.QueueConfig;
import com.dzdp.dto.Result;
import com.dzdp.entity.VoucherOrder;
import com.dzdp.mapper.VoucherOrderMapper;
import com.dzdp.service.ISeckillVoucherService;
import com.dzdp.service.IVoucherOrderService;
import com.dzdp.utils.RedisIdWorker;
import com.dzdp.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;

/**
 * 优惠券订单服务：秒杀生产端（Redis 预扣 + RabbitMQ 投递）与消费端（事务落库）的实现。
 *
 * <p>消息队列实现只有 RabbitMQ 一套：早期用内存阻塞队列 ArrayBlockingQueue 与 Redis Stream
 * 实现过的两版代码已删除。原因是内存队列无法跨进程且进程崩溃即丢消息，
 * 而 Redis Stream 需要自己维护消费者组与 pending 列表，可靠性与运维成本都不如专门的 Broker。</p>
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    /** 下面三个前缀与 seckill.lua / seckill_rollback.lua 中拼接的 key 必须保持一致，改动时需同步脚本 */
    private static final String SECKILL_STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String SECKILL_ORDER_KEY_PREFIX = "seckill:order:";
    private static final String SECKILL_ROLLBACK_KEY_PREFIX = "seckill:rollback:";

    /** 回滚标记存活时间：保证补偿幂等，同时不会永久占用 Redis 内存 */
    private static final long ROLLBACK_MARK_TTL_MILLIS = 3_600_000L;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private static final DefaultRedisScript<Long> SECKILL_ROLLBACK_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);

        SECKILL_ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        SECKILL_ROLLBACK_SCRIPT.setLocation(new ClassPathResource("seckill_rollback.lua"));
        SECKILL_ROLLBACK_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");

        // 1.Redis 原子判定：库存是否充足 + 一人一单 + 预扣库存 + 占位用户，四步在同一个 Lua 内完成
        Long scriptResult = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(),
                voucherId.toString(), userId.toString());
        int result = scriptResult == null ? -1 : scriptResult.intValue();
        if (result != 0) {
            return Result.fail(seckillFailureMessage(result));
        }

        // 2.组装订单消息：请求线程只等一次 Redis，落库与数据库库存扣减交给消费者异步完成
        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);

        String orderJson = JSONUtil.toJsonStr(order);
        try {
            // CorrelationData 里带上订单 JSON：Broker 未确认时回调只能拿到它，
            // 需要靠它定位订单才能回滚 Redis 预扣
            rabbitTemplate.convertAndSend(QueueConfig.X_EXCHANGE, QueueConfig.ROUTING_KEY_XA,
                    orderJson, new CorrelationData(orderJson));
        } catch (Exception e) {
            // 客户端侧就投递失败（例如连接断开）：立即回滚预扣，避免"资格被扣走却没有订单"
            log.error("[MQ] 订单消息投递失败，回滚 Redis 预扣 orderId={} userId={} voucherId={}", orderId, userId, voucherId, e);
            rollbackSeckillQualification(voucherId, userId, orderId);
            return Result.fail("下单失败，请稍后重试");
        }

        // 3.立即返回订单号，前端按订单号异步查询下单结果
        return Result.ok(orderId);
    }

    @Override
    @Transactional
    public boolean createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        // 一人一单：并发下的最终防线是唯一索引 uk_user_voucher，这里只把常见情况挡在写库之前
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            log.warn("[MQ] 用户已购买过该券，跳过 orderId={} userId={} voucherId={}",
                    voucherOrder.getId(), userId, voucherId);
            return false;
        }

        // 库存扣减采用 CAS 写法（stock > 0 才扣），与唯一索引一起保证不超卖、不重复下单
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            // 业务终态：数据库真的没库存了，重试也不会成功，因此返回 false 而不是抛异常触发重投
            log.error("[MQ] 数据库库存不足，订单未创建，Redis 与数据库库存可能不一致，需人工核查 orderId={} voucherId={}",
                    voucherOrder.getId(), voucherId);
            return false;
        }

        // 主键 id 或 uk_user_voucher 冲突会抛 DuplicateKeyException 并回滚整个事务，由调用方按幂等处理
        save(voucherOrder);
        return true;
    }

    @Override
    public void rollbackSeckillQualification(Long voucherId, Long userId, Long orderId) {
        try {
            Long rolledBack = stringRedisTemplate.execute(SECKILL_ROLLBACK_SCRIPT,
                    Arrays.asList(SECKILL_STOCK_KEY_PREFIX + voucherId,
                            SECKILL_ORDER_KEY_PREFIX + voucherId,
                            SECKILL_ROLLBACK_KEY_PREFIX + orderId),
                    String.valueOf(userId),
                    String.valueOf(ROLLBACK_MARK_TTL_MILLIS));
            if (rolledBack != null && rolledBack == 1L) {
                log.warn("[MQ] 已回滚秒杀预扣 orderId={} userId={} voucherId={}", orderId, userId, voucherId);
            } else {
                log.info("[MQ] 秒杀预扣此前已回滚，忽略重复补偿 orderId={}", orderId);
            }
        } catch (Exception e) {
            // 补偿失败只告警不抛出：调用方要么在异常处理里、要么在 MQ 回调线程里，再抛异常会掩盖真实故障
            log.error("[MQ] 回滚秒杀预扣失败，需人工核查 orderId={} userId={} voucherId={}", orderId, userId, voucherId, e);
        }
    }

    private String seckillFailureMessage(int result) {
        switch (result) {
            case 1:
                return "库存不足";
            case 2:
                return "不能重复下单";
            default:
                // -1：Redis 中不存在该券的库存 key，通常意味着活动未初始化或已结束
                return "秒杀商品不存在或已下架";
        }
    }
}

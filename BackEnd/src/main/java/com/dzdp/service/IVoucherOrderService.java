package com.dzdp.service;

import com.dzdp.dto.Result;
import com.dzdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 优惠券订单服务。
 *
 * <p>秒杀链路被拆成"生产"与"消费"两段：{@link #seckillVoucher(Long)} 在请求线程里做 Redis 原子预扣并把消息投给
 * RabbitMQ，{@link #createVoucherOrder(VoucherOrder)} 在消费者线程里完成真正的落库与库存扣减。</p>
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀下单（生产端）：Redis 原子校验库存与一人一单、预扣库存并占位，
     * 随后把订单消息投递到 RabbitMQ 并立即返回订单号。
     *
     * @param voucherId 优惠券 id
     * @return 成功时 data 为订单号；库存不足/重复下单/商品不存在时返回失败提示
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 秒杀订单落库（消费端）：一人一单校验、库存 CAS 扣减、订单写入在同一事务内完成。
     *
     * @return true=本次成功落库；false=该用户已购买或数据库库存不足（业务终态，重试也不会成功）
     * @throws org.springframework.dao.DuplicateKeyException 数据库唯一索引拦下重复消息，调用方按幂等处理
     */
    boolean createVoucherOrder(VoucherOrder voucherOrder);

    /**
     * 回滚 Redis 中的秒杀预扣资格：库存 +1 并移除用户占位。
     *
     * <p>用于"消息最终没有进入队列"的场景（发送异常、Broker 未确认、消息无法路由）。
     * 脚本用 {@code seckill:rollback:{orderId}} 标记保证重复调用只会真正回滚一次。</p>
     */
    void rollbackSeckillQualification(Long voucherId, Long userId, Long orderId);
}

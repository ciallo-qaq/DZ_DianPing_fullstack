package com.dzdp.listener;

import cn.hutool.json.JSONUtil;
import com.dzdp.config.QueueConfig;
import com.dzdp.entity.VoucherOrder;
import com.dzdp.service.IVoucherOrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 秒杀订单消费者：QA 正常消费，QD 兜底重试。
 *
 * <p>手动 ACK（application.yaml 中 {@code listener.simple.acknowledge-mode=manual}），三种收尾方式：</p>
 * <ul>
 *   <li>成功落库，或属于业务终态（已购买 / 库存不足）-> {@code basicAck}，消息结束；</li>
 *   <li>技术异常（数据库、Redis 抖动）且来自 QA -> {@code basicNack(requeue=false)}，
 *       经死信交换机 Y / 路由键 YD 落入 QD 延后重试；</li>
 *   <li>来自 QD 的异常 -> QD 是最后一级，没有更下层的死信队列，记 ERROR 后 {@code basicAck}，
 *       避免消息无限重投把 CPU 打满，交由告警人工介入。</li>
 * </ul>
 *
 * <p>幂等由数据库唯一索引兜底：主键 id 拦同一条消息重投，
 * {@code uk_user_voucher(user_id, voucher_id)} 拦一人一单。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillVoucherListener {

    private final IVoucherOrderService voucherOrderService;

    @RabbitListener(queues = QueueConfig.QUEUE_A)
    public void receivedA(Message message, Channel channel) throws IOException {
        handle(message, channel, QueueConfig.QUEUE_A);
    }

    @RabbitListener(queues = QueueConfig.DEAD_LETTER_QUEUE_D)
    public void receivedD(Message message, Channel channel) throws IOException {
        handle(message, channel, QueueConfig.DEAD_LETTER_QUEUE_D);
    }

    private void handle(Message message, Channel channel, String queue) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        VoucherOrder voucherOrder;
        try {
            voucherOrder = JSONUtil.toBean(body, VoucherOrder.class);
        } catch (Exception e) {
            // 毒消息：反序列化失败重投多少次都不会成功，直接确认并告警
            log.error("[MQ] 消息体不是合法订单 JSON，已丢弃 queue={} body={}", queue, body, e);
            channel.basicAck(deliveryTag, false);
            return;
        }
        if (voucherOrder == null || voucherOrder.getId() == null
                || voucherOrder.getUserId() == null || voucherOrder.getVoucherId() == null) {
            log.error("[MQ] 消息内容缺少订单关键字段，已丢弃 queue={} body={}", queue, body);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            boolean created = voucherOrderService.createVoucherOrder(voucherOrder);
            if (created) {
                log.info("[MQ] 订单落库成功 queue={} orderId={} userId={} voucherId={}",
                        queue, voucherOrder.getId(), voucherOrder.getUserId(), voucherOrder.getVoucherId());
            } else {
                log.warn("[MQ] 订单未创建（已购买或库存不足），无需重试 queue={} orderId={}", queue, voucherOrder.getId());
            }
            channel.basicAck(deliveryTag, false);
        } catch (DuplicateKeyException e) {
            // 幂等：唯一索引拦下了重复消息，说明这笔订单此前已经处理过，确认掉即可
            log.warn("[MQ] 重复消息已幂等忽略 queue={} orderId={}", queue, voucherOrder.getId());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[MQ] 订单处理失败 queue={} orderId={} voucherId={}",
                    queue, voucherOrder.getId(), voucherOrder.getVoucherId(), e);
            if (QueueConfig.QUEUE_A.equals(queue)) {
                // 不重回 QA，交给死信交换机 Y/YD 进 QD 延后重试，避免原地重投空转
                channel.basicNack(deliveryTag, false, false);
            } else {
                // 最后一级仍失败：消息内容已完整打日志，确认掉避免无限重投
                log.error("[MQ] 死信队列处理仍失败，消息已放弃，需人工核查 body={}", body);
                channel.basicAck(deliveryTag, false);
            }
        }
    }
}

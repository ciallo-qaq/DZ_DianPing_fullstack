package com.dzdp.config;

import cn.hutool.json.JSONUtil;
import com.dzdp.entity.VoucherOrder;
import com.dzdp.service.IVoucherOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

/**
 * 生产端可靠性配置：把"消息到底有没有进队列"变成可感知的事件。
 *
 * <p>只调用 {@code convertAndSend} 并不能保证投递成功：交换机不存在、队列未绑定、
 * Broker 未持久化，消息都会被静默丢弃，而生产者毫无感知。开启下面两个回调后，
 * 这些失败才能被捕获，并回滚 Redis 里已经预扣的秒杀资格。</p>
 *
 * <ul>
 *   <li><b>confirm（发布确认）</b>：Broker 是否接收到了消息。需要
 *       {@code spring.rabbitmq.publisher-confirm-type=correlated} 才会携带 {@link CorrelationData}；
 *       ack=false 表示消息没进 Broker，必须回滚预扣。</li>
 *   <li><b>return（发布退回）</b>：消息到达交换机但路由不到任何队列。需要
 *       {@code spring.rabbitmq.template.mandatory=true} 才会退回给生产者，同样需要回滚预扣。</li>
 * </ul>
 *
 * <p>本项目使用 Spring AMQP 2.2（Spring Boot 2.3.12），退回回调是 {@code setReturnCallback}；
 * 升级到 Spring AMQP 2.3+ 后应替换为 {@code setReturnsCallback}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitProducerConfig {

    private final RabbitTemplate rabbitTemplate;
    private final IVoucherOrderService voucherOrderService;

    @PostConstruct
    public void registerPublisherCallbacks() {
        // 1.发布确认：Broker 接收成功(ack=true)只记 debug，失败则回滚预扣
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("[MQ] Broker 已确认消息 correlationData={}", correlationData);
                return;
            }
            log.error("[MQ] Broker 未确认消息，回滚 Redis 预扣 cause={} correlationData={}", cause, correlationData);
            // confirm 回调只能拿到 CorrelationData，所以生产端把订单 JSON 放在它的 id 里
            compensate(correlationData == null ? null : correlationData.getId());
        });

        // 2.发布退回：消息进了交换机但没有匹配的队列绑定
        rabbitTemplate.setReturnCallback((message, replyCode, replyText, exchange, routingKey) -> {
            log.error("[MQ] 消息无法路由到任何队列，回滚 Redis 预扣 exchange={} routingKey={} replyCode={} replyText={}",
                    exchange, routingKey, replyCode, replyText);
            // 退回的消息体就是原始订单 JSON，据此定位订单
            compensate(new String(message.getBody(), StandardCharsets.UTF_8));
        });
    }

    /**
     * 按订单 JSON 回滚 Redis 预扣。回滚脚本自带幂等标记，
     * 因此同步 catch 与回调重复触发时也只会真正回滚一次。
     */
    private void compensate(String orderJson) {
        if (orderJson == null || orderJson.trim().isEmpty()) {
            log.error("[MQ] 消息投递失败但无法定位订单，需人工核查秒杀预扣");
            return;
        }
        try {
            VoucherOrder order = JSONUtil.toBean(orderJson, VoucherOrder.class);
            if (order.getId() == null || order.getUserId() == null || order.getVoucherId() == null) {
                log.error("[MQ] 消息内容缺少订单关键字段，无法回滚预扣 json={}", orderJson);
                return;
            }
            voucherOrderService.rollbackSeckillQualification(order.getVoucherId(), order.getUserId(), order.getId());
        } catch (Exception e) {
            log.error("[MQ] 解析订单消息失败，无法回滚预扣 json={}", orderJson, e);
        }
    }
}

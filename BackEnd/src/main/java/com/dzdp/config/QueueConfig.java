package com.dzdp.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 秒杀订单的 RabbitMQ 拓扑：正常队列 + TTL + 死信队列。
 *
 * <pre>
 *  生产者 --(X / XA)--> [QA] 正常队列 (TTL 10s，死信交换机 Y / 路由键 YD)
 *                              │ 消息过期 或 nack(requeue=false)
 *                              ▼
 *                        [Y 死信交换机] --(YD)--> [QD] 死信队列 --> 兜底消费者
 * </pre>
 *
 * <p>QA 带 TTL 并绑定死信的原因：消息长时间没被消费掉时自动转投 QD，
 * 既相当于一次"延迟重试"，也避免消息无限期滞留在 QA。注意经典队列的队列级 TTL
 * 是在消息到达队头时才判断过期的，所以消费者在线时主要靠消费者主动 nack 触发死信。</p>
 */
@Configuration
public class QueueConfig {

    /** 正常交换机：接收生产者投递的订单消息 */
    public static final String X_EXCHANGE = "X";

    /** 死信交换机：接收 QA 中过期或被拒绝的消息 */
    public static final String Y_DEAD_LETTER_EXCHANGE = "Y";

    /** 正常队列：TTL 10 秒，到期或被拒绝后进入死信交换机 */
    public static final String QUEUE_A = "QA";

    /** 死信队列：兜底重试，无 TTL、无下级死信 */
    public static final String DEAD_LETTER_QUEUE_D = "QD";

    /** 生产者投递订单消息使用的路由键（X -> QA） */
    public static final String ROUTING_KEY_XA = "XA";

    /** QA 死信转发使用的路由键（Y -> QD） */
    public static final String ROUTING_KEY_YD = "YD";

    /** QA 中消息的存活时间（毫秒） */
    private static final int QUEUE_A_MESSAGE_TTL_MILLIS = 10_000;

    @Bean("xExchange")
    public DirectExchange xExchange() {
        return new DirectExchange(X_EXCHANGE);
    }

    @Bean("yExchange")
    public DirectExchange yExchange() {
        return new DirectExchange(Y_DEAD_LETTER_EXCHANGE);
    }

    @Bean("queueA")
    public Queue queueA() {
        Map<String, Object> arguments = new HashMap<>();
        // 声明死信交换机与死信路由键：QA 中过期或被拒绝的消息会被转发到 Y 交换机
        arguments.put("x-dead-letter-exchange", Y_DEAD_LETTER_EXCHANGE);
        arguments.put("x-dead-letter-routing-key", ROUTING_KEY_YD);
        arguments.put("x-message-ttl", QUEUE_A_MESSAGE_TTL_MILLIS);
        return QueueBuilder.durable(QUEUE_A)
                .withArguments(arguments)
                .build();
    }

    @Bean("queueD")
    public Queue queueD() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE_D)
                .build();
    }

    @Bean
    public Binding queueABindingX(@Qualifier("queueA") Queue queueA,
                                  @Qualifier("xExchange") DirectExchange xExchange) {
        return BindingBuilder.bind(queueA).to(xExchange).with(ROUTING_KEY_XA);
    }

    @Bean
    public Binding queueDBindingY(@Qualifier("queueD") Queue queueD,
                                  @Qualifier("yExchange") DirectExchange yExchange) {
        return BindingBuilder.bind(queueD).to(yExchange).with(ROUTING_KEY_YD);
    }
}

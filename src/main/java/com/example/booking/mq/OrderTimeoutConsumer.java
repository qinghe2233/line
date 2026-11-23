package com.example.booking.mq;

import com.example.booking.common.MqConst;
import com.example.booking.event.AppointmentCreatedEvent;
import com.example.booking.service.OrderTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 延时关单消费者。
 * 消费失败（数据库瞬时异常）直接抛异常，让 RocketMQ 按消费组重试；
 * 业务上"订单不存在/已支付/已取消"都不抛异常，正常 ack，避免无意义重试。
 *
 * booking.mq.consumer-enabled=false 时不注册：本地没启动 RocketMQ，
 * 消费者启动会去连 NameServer 失败并导致整个应用启动失败。
 * 关掉消费者后，超时关单仍由 OrderTimeoutCompensateTask 定时扫表兜底，功能可完整验证。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "booking.mq", name = "consumer-enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = MqConst.ORDER_TOPIC,
        selectorExpression = MqConst.ORDER_TAG_TIMEOUT_CLOSE,
        consumerGroup = MqConst.CONSUMER_GROUP_ORDER_TIMEOUT)
public class OrderTimeoutConsumer implements RocketMQListener<AppointmentCreatedEvent> {

    private final OrderTimeoutService orderTimeoutService;

    @Override
    public void onMessage(AppointmentCreatedEvent event) {
        log.info("收到延时关单消息 orderId={} orderNo={}", event.orderId(), event.orderNo());
        orderTimeoutService.handleTimeout(event.orderId());
    }
}

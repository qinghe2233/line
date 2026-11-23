package com.example.booking.mq;

import com.example.booking.common.MqConst;
import com.example.booking.event.TicketCalledEvent;
import com.example.booking.service.QueueTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 叫号 3 分钟延时过号消费者。
 *
 * 业务上"号码不存在/已不是叫号中"都正常 ack，不抛异常，避免无意义重试；
 * 数据库瞬时异常直接抛，让 RocketMQ 重试。
 * 本地未启动 RocketMQ 时通过 booking.mq.consumer-enabled=false 关闭注册，
 * 由定时补偿任务兜底（否则消费者启动连不上 NameServer 会导致整个应用启动失败）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "booking.mq", name = "consumer-enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = MqConst.QUEUE_TOPIC,
        selectorExpression = MqConst.QUEUE_TAG_CALL_TIMEOUT,
        consumerGroup = MqConst.CONSUMER_GROUP_QUEUE_CALL_TIMEOUT)
public class QueueCallTimeoutConsumer implements RocketMQListener<TicketCalledEvent> {

    private final QueueTicketService queueTicketService;

    @Override
    public void onMessage(TicketCalledEvent event) {
        log.info("收到过号延时消息 ticketId={}", event.ticketId());
        queueTicketService.handleCallTimeout(event.ticketId());
    }
}

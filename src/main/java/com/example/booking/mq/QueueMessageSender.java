package com.example.booking.mq;

import com.example.booking.common.MqConst;
import com.example.booking.event.TicketCalledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 叫号后的延时过号消息。
 *
 * 只在叫号事务提交后投递：事务回滚就不会产生消息，避免出现"号没过、消息却把号过了"。
 * 发送失败只记日志，由 QueueCallTimeoutCompensateTask 定时扫表兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueMessageSender {

    private static final long SEND_TIMEOUT_MILLIS = 3000L;

    private final RocketMQTemplate rocketMQTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketCalled(TicketCalledEvent event) {
        Message<TicketCalledEvent> message = MessageBuilder.withPayload(event)
                .setHeader(RocketMQHeaders.KEYS, String.valueOf(event.ticketId()))
                .build();
        try {
            rocketMQTemplate.asyncSend(
                    MqConst.QUEUE_TOPIC + ":" + MqConst.QUEUE_TAG_CALL_TIMEOUT,
                    message,
                    new SendCallback() {
                        @Override
                        public void onSuccess(SendResult sendResult) {
                            log.info("过号延时消息已投递 ticketId={} msgId={}", event.ticketId(), sendResult.getMsgId());
                        }

                        @Override
                        public void onException(Throwable e) {
                            log.error("过号延时消息投递失败，等待定时扫描兜底 ticketId={}", event.ticketId(), e);
                        }
                    },
                    SEND_TIMEOUT_MILLIS,
                    MqConst.DELAY_LEVEL_3_MIN);
        } catch (Exception e) {
            log.error("过号延时消息发送异常，等待定时扫描兜底 ticketId={}", event.ticketId(), e);
        }
    }
}

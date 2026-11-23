package com.example.booking.mq;

import com.example.booking.common.MqConst;
import com.example.booking.event.OrderPaidEvent;
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
 * 支付成功通知消息。
 * 只在支付事务提交后发送；发送失败只记日志，不影响用户支付结果。
 * 通知属于可丢失数据：用户可在"我的消息"里靠订单状态重新渲染，不需要强一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentMessageSender {

    private static final long SEND_TIMEOUT_MILLIS = 3000L;

    private final RocketMQTemplate rocketMQTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        Message<OrderPaidEvent> message = MessageBuilder.withPayload(event)
                .setHeader(RocketMQHeaders.KEYS, event.orderNo())
                .build();
        try {
            rocketMQTemplate.asyncSend(
                    MqConst.NOTIFY_TOPIC + ":" + MqConst.NOTIFY_TAG_PAYMENT_SUCCESS,
                    message,
                    new SendCallback() {
                        @Override
                        public void onSuccess(SendResult sendResult) {
                            log.info("支付成功通知已投递 orderNo={} msgId={}", event.orderNo(), sendResult.getMsgId());
                        }

                        @Override
                        public void onException(Throwable e) {
                            log.error("支付成功通知投递失败 orderNo={}", event.orderNo(), e);
                        }
                    },
                    SEND_TIMEOUT_MILLIS);
        } catch (Exception e) {
            log.error("支付成功通知发送异常 orderNo={}", event.orderNo(), e);
        }
    }
}

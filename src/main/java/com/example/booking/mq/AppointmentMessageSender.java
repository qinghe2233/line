package com.example.booking.mq;

import com.example.booking.common.MqConst;
import com.example.booking.event.AppointmentCreatedEvent;
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
 * 预约创建后的异步动作：投递延时关单消息 + 预约成功通知。
 * 全部在事务提交后执行（AFTER_COMMIT），原因：
 * 1) 事务未提交时发消息，消费端可能查到"还不存在的订单"，或事务最终回滚导致脏消息；
 * 2) 发送失败不能影响已经提交的订单——失败只记日志，由定时扫描任务兜底关单。
 * 用 asyncSend 而不是 syncSend：不让 MQ 的耗时进入用户下单的响应时间。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentMessageSender {

    private static final long SEND_TIMEOUT_MILLIS = 3000L;

    private final RocketMQTemplate rocketMQTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentCreated(AppointmentCreatedEvent event) {
        sendTimeoutClose(event.orderId(), event.orderNo(), event.userId(),
                event.payDeadlineMillis(), MqConst.DELAY_LEVEL_10_MIN);
        sendCreatedNotification(event);
    }

    /**
     * 延时关单消息。也供超时消费者"未到截止时间时补投剩余时长"复用。
     * 消费端逻辑：订单仍是待支付且 now >= payDeadline -> 关单并回滚库存；否则按剩余时间再补投。
     */
    public void sendTimeoutClose(Long orderId, String orderNo, Long userId,
                                 long payDeadlineMillis, int delayLevel) {
        Message<AppointmentCreatedEvent> message = buildMessage(
                new AppointmentCreatedEvent(orderId, orderNo, userId, payDeadlineMillis));
        try {
            rocketMQTemplate.asyncSend(
                    MqConst.ORDER_TOPIC + ":" + MqConst.ORDER_TAG_TIMEOUT_CLOSE,
                    message,
                    new SendCallback() {
                        @Override
                        public void onSuccess(SendResult sendResult) {
                            log.info("延时关单消息已投递 orderNo={} delayLevel={} msgId={}",
                                    orderNo, delayLevel, sendResult.getMsgId());
                        }

                        @Override
                        public void onException(Throwable e) {
                            // 不抛异常：订单已提交，消息丢失由定时扫描兜底
                            log.error("延时关单消息投递失败，等待定时扫描兜底 orderNo={}", orderNo, e);
                        }
                    },
                    SEND_TIMEOUT_MILLIS,
                    delayLevel);
        } catch (Exception e) {
            log.error("延时关单消息发送异常，等待定时扫描兜底 orderNo={}", orderNo, e);
        }
    }

    /** 预约成功通知，由通知模块消费后落 notification 表 */
    private void sendCreatedNotification(AppointmentCreatedEvent event) {
        Message<AppointmentCreatedEvent> message = buildMessage(event);
        try {
            rocketMQTemplate.asyncSend(
                    MqConst.NOTIFY_TOPIC + ":" + MqConst.NOTIFY_TAG_APPOINTMENT_CREATED,
                    message,
                    new SendCallback() {
                        @Override
                        public void onSuccess(SendResult sendResult) {
                            log.info("预约成功通知已投递 orderNo={}", event.orderNo());
                        }

                        @Override
                        public void onException(Throwable e) {
                            log.error("预约成功通知投递失败 orderNo={}", event.orderNo(), e);
                        }
                    },
                    SEND_TIMEOUT_MILLIS);
        } catch (Exception e) {
            log.error("预约成功通知发送异常 orderNo={}", event.orderNo(), e);
        }
    }

    /** KEYS 设为订单号，便于在 RocketMQ 控制台按业务单号检索消息 */
    private Message<AppointmentCreatedEvent> buildMessage(AppointmentCreatedEvent event) {
        return MessageBuilder.withPayload(event)
                .setHeader(RocketMQHeaders.KEYS, event.orderNo())
                .build();
    }
}

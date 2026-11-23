package com.example.booking.event;

/**
 * 预约创建成功事件（也是延时关单消息的载荷）。
 * 只在事务内发布，监听器用 @TransactionalEventListener(AFTER_COMMIT) 消费，
 * 保证"消息发出"一定对应"订单已提交"，不会出现库存已回滚但关单消息已发出的脏消息。
 *
 * payDeadline 用毫秒时间戳而不是 LocalDateTime：
 * MQ 序列化器（Jackson）不一定注册 JavaTimeModule，时间戳能避免跨生产/消费端的格式问题。
 */
public record AppointmentCreatedEvent(
        Long orderId,
        String orderNo,
        Long userId,
        long payDeadlineMillis
) {
}

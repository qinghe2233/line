package com.example.booking.event;

/**
 * 排队号被叫号事件（也是 3 分钟过号延时消息的载荷）。
 * 在「叫号」事务内发布，监听器 AFTER_COMMIT 才投递延时消息，
 * 保证不会出现"消息已发、叫号事务却回滚"的情况。
 * 时间用毫秒时间戳，避免 MQ Jackson 序列化器未注册 JavaTimeModule 的兼容问题。
 */
public record TicketCalledEvent(
        Long ticketId,
        Long storeId,
        Long serviceId,
        long calledAtMillis
) {
}

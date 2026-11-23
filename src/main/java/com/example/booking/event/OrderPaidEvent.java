package com.example.booking.event;

import java.math.BigDecimal;

/**
 * 支付成功事件。
 * 事务内发布，监听器 AFTER_COMMIT 才真正发 MQ，保证"通知已发"对应"支付与积分已提交"。
 */
public record OrderPaidEvent(
        Long orderId,
        String orderNo,
        Long userId,
        BigDecimal amount,
        String tradeNo,
        int points) {
}

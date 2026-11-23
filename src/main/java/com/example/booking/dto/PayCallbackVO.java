package com.example.booking.dto;

/**
 * 支付回调结果。
 * duplicated = true 表示这是一次重复回调，服务端没有重复处理（没扣积分、没重复改状态、没重复发通知）。
 */
public record PayCallbackVO(
        String orderNo,
        String tradeNo,
        Integer status,
        String statusDesc,
        boolean duplicated) {
}

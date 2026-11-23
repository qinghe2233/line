package com.example.booking.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 模拟支付入参。
 * 前端只传订单号，金额、流水号、签名都由服务端生成，
 * 避免把签名密钥暴露给浏览器，也避免客户端伪造金额。
 */
public record MockPayRequest(@NotBlank(message = "订单号不能为空") String orderNo) {
}

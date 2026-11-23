package com.example.booking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 支付渠道回调入参（模拟）。
 * sign 算法：MD5(orderNo|tradeNo|amount|status|secret) 转大写，
 * 真实项目一般用渠道公钥验签（RSA/SHA256withRSA），这里只演示验签位置与失败处理。
 */
public record PayCallbackRequest(
        @NotBlank String orderNo,
        @NotBlank String tradeNo,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank String status,
        @NotBlank String sign) {
}

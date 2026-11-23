package com.example.booking.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * JWT 配置。
 * 启动时校验 secret 长度：HS256 要求密钥不少于 256 bit（32 字节），
 * 配短了签名强度不足，不如直接启动失败。
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "booking.jwt")
public class JwtProperties {

    @NotBlank(message = "JWT 密钥不能为空")
    @Size(min = 32, message = "JWT 密钥至少 32 个字符（HS256 要求 256 bit）")
    private String secret;

    @Min(value = 1, message = "JWT 有效期至少 1 分钟")
    private long expireMinutes = 120;

    @NotBlank(message = "JWT 签发者不能为空")
    private String issuer = "booking-queue";
}

package com.example.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 连通性测试返回对象。
 * 用 record 而不是 class：DTO 只承载数据，不需要 setter，也不该被随手修改。
 */
@Schema(description = "连通性测试结果")
public record PingVO(
        @Schema(description = "固定返回 pong") String message,
        @Schema(description = "服务端当前时间") java.time.LocalDateTime serverTime) {
}

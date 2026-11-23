package com.example.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 现场取号请求。
 * bookingOrderId 可选：预约到店的顾客可以带上预约单，散客取号不传。
 */
@Schema(description = "取号请求")
public record TakeTicketRequest(

        @Schema(description = "门店 ID", example = "1")
        @NotNull(message = "门店 ID 不能为空")
        Long storeId,

        @Schema(description = "服务项 ID", example = "1")
        @NotNull(message = "服务项 ID 不能为空")
        Long serviceId,

        @Schema(description = "关联预约单 ID，散客取号可不传")
        Long bookingOrderId
) {
}

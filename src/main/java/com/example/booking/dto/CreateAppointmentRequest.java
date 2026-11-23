package com.example.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 创建预约请求。
 * 只允许客户端传 slotId：门店、服务、资源、价格全部由服务端根据时段反查，
 * 避免客户端伪造金额或跨门店下单。
 */
@Schema(description = "创建预约请求")
public record CreateAppointmentRequest(

        @Schema(description = "时段库存 ID", example = "1")
        @NotNull(message = "时段不能为空")
        @Positive(message = "时段 ID 不合法")
        Long slotId,

        @Schema(description = "顾客备注", example = "希望安排店长剪发")
        @Size(max = 255, message = "备注最多 255 个字符")
        String remark
) {
}

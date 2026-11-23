package com.example.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 管理端新增 / 修改服务项。 */
@Schema(description = "服务项保存请求")
public record ServiceItemRequest(

        @NotNull(message = "门店不能为空")
        Long storeId,

        @NotBlank(message = "服务名称不能为空")
        @Size(max = 64, message = "服务名称最多 64 个字符")
        String name,

        @NotNull(message = "服务时长不能为空")
        @Min(value = 5, message = "服务时长至少 5 分钟")
        @Max(value = 600, message = "服务时长最多 600 分钟")
        Integer durationMinutes,

        @Min(value = 0, message = "缓冲时长不能为负")
        @Max(value = 120, message = "缓冲时长最多 120 分钟")
        Integer bufferMinutes,

        @NotNull(message = "价格不能为空")
        @DecimalMin(value = "0.00", message = "价格不能为负")
        BigDecimal price,

        @Size(max = 500, message = "描述最多 500 个字符")
        String description,

        Integer sortNo,

        @Min(value = 0, message = "状态只能是 0 或 1")
        @Max(value = 1, message = "状态只能是 0 或 1")
        Integer status
) {
}

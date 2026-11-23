package com.example.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/** 管理端新增 / 修改排班时段。 */
@Schema(description = "排班时段保存请求")
public record SlotSaveRequest(

        @NotNull(message = "门店不能为空")
        Long storeId,

        @NotNull(message = "服务项不能为空")
        Long serviceId,

        @NotNull(message = "资源不能为空")
        Long resourceId,

        @NotNull(message = "排班日期不能为空")
        LocalDate slotDate,

        @NotNull(message = "开始时间不能为空")
        LocalTime startTime,

        @NotNull(message = "结束时间不能为空")
        LocalTime endTime,

        @NotNull(message = "名额不能为空")
        @Min(value = 1, message = "名额至少为 1")
        @Max(value = 999, message = "名额最多 999")
        Integer totalStock,

        @Min(value = 0, message = "状态只能是 0 或 1")
        @Max(value = 1, message = "状态只能是 0 或 1")
        Integer status
) {
}

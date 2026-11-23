package com.example.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 创建预约的返回结果。
 * 前端拿到 payDeadline 后展示"请在 xx:xx 前完成支付"，并用它启动倒计时，
 * 不依赖服务端再查一次详情。
 */
@Schema(description = "预约单简要信息")
public record AppointmentVO(

        @Schema(description = "订单 ID") Long orderId,
        @Schema(description = "业务单号") String orderNo,
        @Schema(description = "状态码：0-待支付 10-已支付 ...") Integer status,
        @Schema(description = "状态描述") String statusDesc,
        @Schema(description = "应付金额（元）") BigDecimal amount,
        @Schema(description = "预约日期") LocalDate slotDate,
        @Schema(description = "开始时间") LocalTime startTime,
        @Schema(description = "结束时间") LocalTime endTime,
        @Schema(description = "服务名称") String serviceName,
        @Schema(description = "支付截止时间") LocalDateTime payDeadline
) {
}

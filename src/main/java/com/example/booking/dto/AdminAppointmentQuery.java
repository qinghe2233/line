package com.example.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 管理端预约列表筛选条件。
 * 用 @Data 类而不是 record：Spring MVC 的 query 参数绑定依赖 setter，
 * 这样 /api/admin/appointments?storeId=1&status=10 才能直接映射进来。
 */
@Data
@Schema(description = "管理端预约列表筛选条件")
public class AdminAppointmentQuery {

    @Schema(description = "页码，从 1 开始", example = "1")
    private Long page = 1L;

    @Schema(description = "每页条数，最大 500", example = "10")
    private Long size = 10L;

    @Schema(description = "门店 ID")
    private Long storeId;

    @Schema(description = "订单状态：0-待支付 10-已支付 20-已核销 30-已完成 35-爽约 40-用户取消 50-超时取消")
    private Integer status;

    @Schema(description = "预约日期（精确匹配），如 2026-11-21")
    private LocalDate slotDate;

    @Schema(description = "预约日期范围开始（含）")
    private LocalDate startDate;

    @Schema(description = "预约日期范围结束（含）")
    private LocalDate endDate;

    @Schema(description = "订单号，支持模糊匹配")
    private String orderNo;

    @Schema(description = "用户手机号，支持前缀匹配")
    private String phone;
}

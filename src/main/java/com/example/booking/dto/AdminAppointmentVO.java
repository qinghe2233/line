package com.example.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 管理端预约列表行。
 * 手机号/昵称来自 sys_user，用批量查询补齐，避免每行一次用户查询（N+1）。
 */
@Schema(description = "管理端预约列表行")
public record AdminAppointmentVO(

        Long id,
        String orderNo,
        Long userId,
        String userPhone,
        String userNickname,
        Long storeId,
        String serviceName,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal amount,
        BigDecimal paidAmount,
        Integer status,
        String statusDesc,
        LocalDateTime verifyTime,
        LocalDateTime createdAt
) {
}

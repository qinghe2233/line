package com.example.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 管理端排班行。
 * 「排班」在库里落地为 time_slot_stock（时段库存），它直接决定顾客能不能约，
 * 所以管理页维护的就是这张表，而不是另建一套 schedule 表再同步。
 */
@Schema(description = "排班时段")
public record SlotVO(
        Long id,
        Long storeId,
        Long serviceId,
        String serviceName,
        Long resourceId,
        String resourceName,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime,
        Integer totalStock,
        Integer remainStock,
        Integer status
) {
}

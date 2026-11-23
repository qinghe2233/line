package com.example.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 排队号视图。「前面还有 N 人」只在状态为等待中时有意义，其余状态返回 null。
 */
@Schema(description = "排队号")
public record QueueTicketVO(

        Long id,
        String ticketNo,
        Integer seqNo,
        Long storeId,
        Long serviceId,
        LocalDate ticketDate,
        Integer status,
        String statusDesc,
        @Schema(description = "前面还有多少人（仅等待中有效）")
        Long waitingAhead,
        LocalDateTime calledAt,
        LocalDateTime createdAt
) {
}

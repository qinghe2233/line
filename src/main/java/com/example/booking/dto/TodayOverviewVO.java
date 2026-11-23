package com.example.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * 管理端今日概览。
 * 预约维度按 slot_date = 今日统计，排队维度按 ticket_date = 今日统计。
 */
@Schema(description = "今日概览")
public record TodayOverviewVO(

        LocalDate date,

        @Schema(description = "今日预约总数")
        long bookingTotal,
        long pendingPay,
        long paid,
        long verified,
        long finished,
        long noShow,
        @Schema(description = "用户取消 + 超时取消")
        long canceled,

        @Schema(description = "今日取号总数")
        long ticketTotal,
        long waiting,
        long calling,
        long ticketFinished,
        long passed
) {
}

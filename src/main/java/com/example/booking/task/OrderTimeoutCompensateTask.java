package com.example.booking.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.booking.domain.BookingOrder;
import com.example.booking.domain.enums.OrderStatus;
import com.example.booking.mapper.BookingOrderMapper;
import com.example.booking.service.OrderTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时关单兜底扫描（最简补偿实现）。
 *
 * 为什么还需要它：延时消息只保证"大概率按时到达"，消息可能丢失、消费者可能连续失败。
 * 每分钟扫一次"待支付且已过截止时间"的订单，复用同一套关单逻辑，
 * 靠状态机条件更新保证和 MQ 消费不会重复关单。
 *
 * 为什么不用本地消息表：本场景订单本身就是"消息源"，扫描订单表即可，不需要额外 outbox。
 * 单机定时任务不需要分布式锁：多实例同时扫，状态机条件更新也只会成功一次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutCompensateTask {

    private static final int BATCH_SIZE = 200;

    private final BookingOrderMapper bookingOrderMapper;
    private final OrderTimeoutService orderTimeoutService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void compensateExpiredOrders() {
        List<BookingOrder> expired = bookingOrderMapper.selectList(Wrappers.<BookingOrder>lambdaQuery()
                .select(BookingOrder::getId)
                .eq(BookingOrder::getStatus, OrderStatus.PENDING_PAY)
                .lt(BookingOrder::getPayDeadline, LocalDateTime.now())
                .orderByAsc(BookingOrder::getId)
                .last("LIMIT " + BATCH_SIZE));
        if (expired.isEmpty()) {
            return;
        }
        log.info("超时关单补偿扫描到 {} 笔订单", expired.size());
        for (BookingOrder order : expired) {
            try {
                orderTimeoutService.handleTimeout(order.getId());
            } catch (Exception e) {
                // 单笔失败不影响其余订单，下一分钟还会再扫到
                log.error("补偿关单失败 orderId={}", order.getId(), e);
            }
        }
    }
}

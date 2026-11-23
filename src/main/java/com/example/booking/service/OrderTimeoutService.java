package com.example.booking.service;

import com.example.booking.common.BizException;
import com.example.booking.common.MqConst;
import com.example.booking.domain.BookingOrder;
import com.example.booking.domain.enums.OrderStatus;
import com.example.booking.mapper.BookingOrderMapper;
import com.example.booking.mapper.TimeSlotStockMapper;
import com.example.booking.mq.AppointmentMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 超时关单。
 *
 * 消费幂等（防重复回滚）：
 * 1) 方法开头判状态：不是"待支付"直接返回，说明已支付/已取消，重复消息不做任何事；
 * 2) 状态机条件更新 WHERE status = PENDING_PAY：并发消费时只有一个能把状态推到"超时取消"；
 * 3) restoreOne 的 WHERE remain_stock < total_stock：即使前两道都被突破，也不会把库存加超；
 * 4) 回滚库存与改状态在同一个事务里，要么都成功，要么都回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTimeoutService {

    private final BookingOrderMapper bookingOrderMapper;
    private final TimeSlotStockMapper timeSlotStockMapper;
    private final AppointmentService appointmentService;
    private final AppointmentMessageSender messageSender;

    @Transactional
    public void handleTimeout(Long orderId) {
        BookingOrder order = bookingOrderMapper.selectById(orderId);
        if (order == null) {
            log.warn("超时消息对应的订单不存在，忽略 orderId={}", orderId);
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING_PAY) {
            log.info("订单已处理，跳过超时关单 orderId={} status={}", orderId, order.getStatus().getDesc());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(order.getPayDeadline())) {
            // 10 分钟档先到：还没到 15 分钟截止时间，补投剩余时长
            long remainMillis = Duration.between(now, order.getPayDeadline()).toMillis();
            int delayLevel = delayLevelFor(remainMillis);
            messageSender.sendTimeoutClose(order.getId(), order.getOrderNo(), order.getUserId(),
                    order.getPayDeadline().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), delayLevel);
            log.info("未到支付截止时间，补投延时消息 orderId={} remainSeconds={} delayLevel={}",
                    orderId, remainMillis / 1000, delayLevel);
            return;
        }

        try {
            // 条件更新：只有仍处于待支付的订单能流转到超时取消，重复消费在这里被拦下
            appointmentService.changeStatus(orderId, OrderStatus.PENDING_PAY, OrderStatus.TIMEOUT_CANCELED);
        } catch (BizException e) {
            log.info("关单时订单状态已被并发修改，忽略 orderId={} reason={}", orderId, e.getMessage());
            return;
        }

        // 与状态更新同事务：状态改失败会回滚，这里执行成功则库存只回滚一次
        timeSlotStockMapper.restoreOne(order.getSlotId());
        // 用目标状态而不是 order.getStatus()：order 是更新前查出来的快照，仍是"待支付"
        log.info("超时关单成功 orderId={} slotId={} status={}", orderId, order.getSlotId(),
                OrderStatus.TIMEOUT_CANCELED.getDesc());
    }

    /** 按剩余时间选 RocketMQ 最接近的延时档位，宁可晚到也不能早到（早到会再补投） */
    static int delayLevelFor(long remainMillis) {
        long seconds = Math.max(1L, remainMillis / 1000);
        if (seconds <= 1) return 1;    // 1s
        if (seconds <= 5) return 2;    // 5s
        if (seconds <= 10) return 3;   // 10s
        if (seconds <= 30) return 4;   // 30s
        if (seconds <= 60) return 5;   // 1m
        if (seconds <= 120) return 6;  // 2m
        if (seconds <= 180) return 7;  // 3m
        if (seconds <= 240) return 8;  // 4m
        return MqConst.DELAY_LEVEL_5_MIN; // 5m
    }
}

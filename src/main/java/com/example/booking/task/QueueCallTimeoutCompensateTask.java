package com.example.booking.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.booking.domain.QueueTicket;
import com.example.booking.domain.enums.TicketStatus;
import com.example.booking.mapper.QueueTicketMapper;
import com.example.booking.service.QueueTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 叫号超时兜底扫描（与延时消息二选一即可，这里同时保留形成双保险）。
 *
 * 每分钟扫一次「叫号中且叫号时间已超过 N 分钟」的号，复用同一个过号方法。
 * 顺序保证靠 idx_status_called (status, called_at)，避免全表扫描。
 * 多实例同时扫也安全：过号用的是 WHERE status = CALLING 条件更新，只会成功一次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueCallTimeoutCompensateTask {

    private static final int BATCH_SIZE = 200;

    private final QueueTicketMapper queueTicketMapper;
    private final QueueTicketService queueTicketService;

    @Value("${queue.call-timeout-minutes:3}")
    private long callTimeoutMinutes;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void compensateCallTimeout() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(callTimeoutMinutes);
        List<QueueTicket> expired = queueTicketMapper.selectList(Wrappers.<QueueTicket>lambdaQuery()
                .select(QueueTicket::getId)
                .eq(QueueTicket::getStatus, TicketStatus.CALLING)
                .lt(QueueTicket::getCalledAt, deadline)
                .orderByAsc(QueueTicket::getId)
                .last("LIMIT " + BATCH_SIZE));
        if (expired.isEmpty()) {
            return;
        }
        log.info("叫号超时补偿扫描到 {} 个号码", expired.size());
        for (QueueTicket ticket : expired) {
            try {
                queueTicketService.handleCallTimeout(ticket.getId());
            } catch (Exception e) {
                // 单条失败不影响其余号码，下一分钟会再扫到
                log.error("补偿过号失败 ticketId={}", ticket.getId(), e);
            }
        }
    }
}

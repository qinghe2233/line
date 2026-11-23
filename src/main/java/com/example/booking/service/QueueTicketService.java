package com.example.booking.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.booking.common.BizException;
import com.example.booking.common.ResultCode;
import com.example.booking.domain.QueueTicket;
import com.example.booking.domain.ServiceItem;
import com.example.booking.domain.Store;
import com.example.booking.domain.enums.TicketStatus;
import com.example.booking.dto.QueueTicketVO;
import com.example.booking.dto.TakeTicketRequest;
import com.example.booking.event.TicketCalledEvent;
import com.example.booking.mapper.QueueTicketMapper;
import com.example.booking.mapper.ServiceItemMapper;
import com.example.booking.mapper.StoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 排队取号 / 叫号 / 完成 / 过号。
 *
 * 并发控制统一使用「条件更新」：
 *   UPDATE queue_ticket SET status = ? WHERE id = ? AND status = ?
 * 返回 0 行就代表状态已被别人改过（例如两个店员同时叫同一个号），直接提示前端刷新，
 * 不依赖 SELECT 后再判断，避免检查与更新之间的竞态窗口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueTicketService {

    private static final int CODE_MIN = 1;
    private static final int STORE_STATUS_OPEN = 1;
    private static final int SERVICE_STATUS_ON_SHELF = 1;
    /** 唯一索引兜底时的最大重新取号次数，正常情况下 Redis 保证一次成功 */
    private static final int MAX_ALLOCATE_RETRY = 3;

    private final QueueTicketMapper queueTicketMapper;
    private final StoreMapper storeMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final QueueSequenceService queueSequenceService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 现场取号。
     * 序号生成（Redis）和落库不在一个原子操作里，所以必须靠 DB 唯一索引兜底：
     * 极端情况下（Redis 丢数据）重试几次拿新号即可，不会出现两张相同的号。
     */
    @Transactional
    public QueueTicketVO take(Long userId, TakeTicketRequest request) {
        Store store = storeMapper.selectById(request.storeId());
        if (store == null) {
            throw new BizException(ResultCode.STORE_NOT_FOUND);
        }
        if (!Integer.valueOf(STORE_STATUS_OPEN).equals(store.getStatus())) {
            throw new BizException(ResultCode.STORE_CLOSED);
        }
        ServiceItem service = serviceItemMapper.selectById(request.serviceId());
        if (service == null || !request.storeId().equals(service.getStoreId())) {
            throw new BizException(ResultCode.SERVICE_NOT_FOUND);
        }
        if (!Integer.valueOf(SERVICE_STATUS_ON_SHELF).equals(service.getStatus())) {
            throw new BizException(ResultCode.SERVICE_OFF_SHELF);
        }

        LocalDate today = LocalDate.now();
        QueueTicket ticket = allocateAndInsert(userId, request, today);
        long ahead = countWaitingAhead(ticket);
        log.info("取号成功 userId={} storeId={} serviceId={} ticketNo={} seq={} ahead={}",
                userId, request.storeId(), request.serviceId(), ticket.getTicketNo(), ticket.getSeqNo(), ahead);
        return toVO(ticket, ahead);
    }

    /** 顾客查询排队进度 */
    public QueueTicketVO progress(Long ticketId) {
        QueueTicket ticket = getExisting(ticketId);
        Long ahead = ticket.getStatus() == TicketStatus.WAITING ? countWaitingAhead(ticket) : null;
        return toVO(ticket, ahead);
    }

    /**
     * 管理端叫号面板：今日号码列表。
     * 走 idx_store_date_status_seq，按 seq_no 升序就是实际的叫号顺序；
     * LIMIT 500 兜底，避免极端情况下一次拉太多行。
     */
    public List<QueueTicketVO> listToday(Long storeId, Integer status) {
        TicketStatus ticketStatus = null;
        if (status != null) {
            ticketStatus = TicketStatus.fromCode(status);
            if (ticketStatus == null) {
                throw new BizException(ResultCode.PARAM_INVALID, "不支持的排队状态：" + status);
            }
        }
        List<QueueTicket> tickets = queueTicketMapper.selectList(Wrappers.<QueueTicket>lambdaQuery()
                .eq(storeId != null, QueueTicket::getStoreId, storeId)
                .eq(ticketStatus != null, QueueTicket::getStatus, ticketStatus)
                .eq(QueueTicket::getTicketDate, LocalDate.now())
                .orderByAsc(QueueTicket::getSeqNo)
                .last("LIMIT 500"));
        return tickets.stream().map(ticket -> toVO(ticket, null)).toList();
    }

    /**
     * 店员叫号：等待中 -> 叫号中。
     * 叫号成功后在事务内发布事件，AFTER_COMMIT 投递 3 分钟延时消息用于自动过号。
     */
    @Transactional
    public QueueTicketVO call(Long ticketId, Long staffId) {
        QueueTicket ticket = getExisting(ticketId);
        if (!ticket.getStatus().canTransferTo(TicketStatus.CALLING)) {
            throw new BizException(ResultCode.TICKET_STATUS_ILLEGAL,
                    String.format("号码当前状态为 %s，不能叫号", ticket.getStatus().getDesc()));
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = queueTicketMapper.update(null, Wrappers.<QueueTicket>lambdaUpdate()
                .eq(QueueTicket::getId, ticketId)
                .eq(QueueTicket::getStatus, TicketStatus.WAITING)
                .set(QueueTicket::getStatus, TicketStatus.CALLING)
                .set(QueueTicket::getCallStaffId, staffId)
                .set(QueueTicket::getCalledAt, now));
        if (updated == 0) {
            // 两个店员同时点叫号：只有一个人能成功，另一个人在这里被拦下
            throw new BizException(ResultCode.TICKET_STATUS_ILLEGAL, "该号码已被其他店员叫号，请刷新");
        }

        eventPublisher.publishEvent(new TicketCalledEvent(
                ticketId, ticket.getStoreId(), ticket.getServiceId(),
                now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));

        ticket.setStatus(TicketStatus.CALLING);
        ticket.setCallStaffId(staffId);
        ticket.setCalledAt(now);
        log.info("叫号成功 ticketId={} ticketNo={} staffId={}", ticketId, ticket.getTicketNo(), staffId);
        return toVO(ticket, null);
    }

    /** 店员完成服务：叫号中 -> 已完成 */
    @Transactional
    public QueueTicketVO finish(Long ticketId) {
        QueueTicket ticket = getExisting(ticketId);
        if (!ticket.getStatus().canTransferTo(TicketStatus.FINISHED)) {
            throw new BizException(ResultCode.TICKET_STATUS_ILLEGAL,
                    String.format("号码当前状态为 %s，不能完成", ticket.getStatus().getDesc()));
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = queueTicketMapper.update(null, Wrappers.<QueueTicket>lambdaUpdate()
                .eq(QueueTicket::getId, ticketId)
                .eq(QueueTicket::getStatus, TicketStatus.CALLING)
                .set(QueueTicket::getStatus, TicketStatus.FINISHED)
                .set(QueueTicket::getFinishedAt, now));
        if (updated == 0) {
            throw new BizException(ResultCode.TICKET_STATUS_ILLEGAL, "号码状态已变化，请刷新");
        }

        ticket.setStatus(TicketStatus.FINISHED);
        ticket.setFinishedAt(now);
        log.info("排队完成 ticketId={} ticketNo={}", ticketId, ticket.getTicketNo());
        return toVO(ticket, null);
    }

    /**
     * 叫号超时自动过号（延时消息消费者 + 定时补偿任务共用）。
     *
     * 幂等：先判状态，再用 WHERE status = CALLING 条件更新。
     * 重复消息 / 消息与补偿任务并发时，只有一次更新能成功，其余直接返回，不会把已完成的服务改成过号。
     */
    @Transactional
    public void handleCallTimeout(Long ticketId) {
        QueueTicket ticket = queueTicketMapper.selectById(ticketId);
        if (ticket == null) {
            log.warn("过号消息对应的号码不存在，忽略 ticketId={}", ticketId);
            return;
        }
        if (ticket.getStatus() != TicketStatus.CALLING) {
            log.info("号码已非叫号中，跳过自动过号 ticketId={} status={}", ticketId, ticket.getStatus().getDesc());
            return;
        }

        int updated = queueTicketMapper.update(null, Wrappers.<QueueTicket>lambdaUpdate()
                .eq(QueueTicket::getId, ticketId)
                .eq(QueueTicket::getStatus, TicketStatus.CALLING)
                .set(QueueTicket::getStatus, TicketStatus.PASSED));
        if (updated == 1) {
            log.info("叫号超时，自动过号 ticketId={} ticketNo={}", ticketId, ticket.getTicketNo());
        }
    }

    /**
     * 序号分配 + 插入，遇到唯一索引冲突重新取号。
     * Redis 是主要发号器，唯一索引只在 Redis 数据异常时兜底，所以重试 3 次足够。
     */
    private QueueTicket allocateAndInsert(Long userId, TakeTicketRequest request, LocalDate ticketDate) {
        for (int attempt = 1; attempt <= MAX_ALLOCATE_RETRY; attempt++) {
            long seq = queueSequenceService.nextSeq(request.storeId(), request.serviceId(), ticketDate);
            QueueTicket ticket = new QueueTicket();
            ticket.setTicketNo(buildTicketNo(request.serviceId(), seq));
            ticket.setSeqNo(Math.toIntExact(seq));
            ticket.setStoreId(request.storeId());
            ticket.setUserId(userId);
            ticket.setServiceId(request.serviceId());
            ticket.setBookingOrderId(request.bookingOrderId());
            ticket.setTicketDate(ticketDate);
            ticket.setStatus(TicketStatus.WAITING);
            try {
                queueTicketMapper.insert(ticket);
                return ticket;
            } catch (DuplicateKeyException e) {
                log.warn("排队号唯一索引冲突，重新取号 attempt={} storeId={} serviceId={} seq={}",
                        attempt, request.storeId(), request.serviceId(), seq);
            }
        }
        throw new BizException(ResultCode.SYSTEM_ERROR, "号码分配冲突，请稍后重试");
    }

    /** 展示号：服务项映射字母前缀 + 3 位补齐，如服务 1 -> A001 */
    private static String buildTicketNo(Long serviceId, long seq) {
        int index = Math.floorMod(Math.toIntExact(serviceId) - CODE_MIN, 26);
        char prefix = (char) ('A' + index);
        return prefix + String.format("%03d", seq);
    }

    /** 前面还有 N 人：同门店、同服务项、同日期、等待中且序号更小的人数 */
    private long countWaitingAhead(QueueTicket ticket) {
        Long count = queueTicketMapper.selectCount(Wrappers.<QueueTicket>lambdaQuery()
                .eq(QueueTicket::getStoreId, ticket.getStoreId())
                .eq(QueueTicket::getServiceId, ticket.getServiceId())
                .eq(QueueTicket::getTicketDate, ticket.getTicketDate())
                .eq(QueueTicket::getStatus, TicketStatus.WAITING)
                .lt(QueueTicket::getSeqNo, ticket.getSeqNo()));
        return count == null ? 0L : count;
    }

    private QueueTicket getExisting(Long ticketId) {
        QueueTicket ticket = queueTicketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BizException(ResultCode.TICKET_NOT_FOUND);
        }
        return ticket;
    }

    private QueueTicketVO toVO(QueueTicket ticket, Long waitingAhead) {
        return new QueueTicketVO(
                ticket.getId(),
                ticket.getTicketNo(),
                ticket.getSeqNo(),
                ticket.getStoreId(),
                ticket.getServiceId(),
                ticket.getTicketDate(),
                ticket.getStatus().getCode(),
                ticket.getStatus().getDesc(),
                waitingAhead,
                ticket.getCalledAt(),
                ticket.getCreatedAt());
    }
}

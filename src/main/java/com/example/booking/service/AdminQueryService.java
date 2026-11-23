package com.example.booking.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.booking.common.BizException;
import com.example.booking.common.PageResult;
import com.example.booking.common.ResultCode;
import com.example.booking.domain.BookingOrder;
import com.example.booking.domain.SysUser;
import com.example.booking.domain.enums.OrderStatus;
import com.example.booking.dto.AdminAppointmentQuery;
import com.example.booking.dto.AdminAppointmentVO;
import com.example.booking.dto.StatusCount;
import com.example.booking.dto.TodayOverviewVO;
import com.example.booking.mapper.BookingOrderMapper;
import com.example.booking.mapper.QueueTicketMapper;
import com.example.booking.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端只读查询：预约列表分页、今日概览。
 * 写操作（核销/爽约）统一放 AppointmentService，保证状态机只有一份实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminQueryService {

    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final long MAX_PAGE_SIZE = 100L;

    private final BookingOrderMapper bookingOrderMapper;
    private final QueueTicketMapper queueTicketMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 管理端预约列表：多条件筛选 + 分页。
     *
     * 为什么用 MP 的 selectPage + 条件构造器：
     * 1) 每个条件都是可选的，动态 SQL 用构造器比 XML <if> 更直观；
     * 2) 分页插件自动改写 COUNT 与 LIMIT，不需要手写两条 SQL。
     * 手机号筛选先查用户 ID，再 IN 订单表，避免把用户输入拼进 SQL。
     */
    public PageResult<AdminAppointmentVO> pageAppointments(AdminAppointmentQuery query) {
        long pageNum = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        long pageSize = query.getSize() == null || query.getSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(query.getSize(), MAX_PAGE_SIZE);

        LambdaQueryWrapper<BookingOrder> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(query.getStoreId() != null, BookingOrder::getStoreId, query.getStoreId());

        if (query.getStatus() != null) {
            OrderStatus status = OrderStatus.fromCode(query.getStatus());
            if (status == null) {
                throw new BizException(ResultCode.PARAM_INVALID, "不支持的订单状态：" + query.getStatus());
            }
            wrapper.eq(BookingOrder::getStatus, status);
        }

        wrapper.eq(query.getSlotDate() != null, BookingOrder::getSlotDate, query.getSlotDate())
                .ge(query.getStartDate() != null, BookingOrder::getSlotDate, query.getStartDate())
                .le(query.getEndDate() != null, BookingOrder::getSlotDate, query.getEndDate())
                .like(StringUtils.hasText(query.getOrderNo()), BookingOrder::getOrderNo,
                        query.getOrderNo() == null ? null : query.getOrderNo().trim());

        if (StringUtils.hasText(query.getPhone())) {
            List<Long> userIds = sysUserMapper.selectIdsByPhonePrefix(query.getPhone().trim());
            if (userIds.isEmpty()) {
                return PageResult.of(pageNum, pageSize, 0, 0, Collections.emptyList());
            }
            wrapper.in(BookingOrder::getUserId, userIds);
        }

        // 按主键倒序：InnoDB 二级索引天然包含主键，分页翻得深时也可改用 id 游标避免大 offset
        wrapper.orderByDesc(BookingOrder::getId);

        Page<BookingOrder> page = bookingOrderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        Map<Long, SysUser> userMap = loadUsers(page.getRecords());
        List<AdminAppointmentVO> records = page.getRecords().stream()
                .map(order -> toVO(order, userMap.get(order.getUserId())))
                .toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getPages(), records);
    }

    /**
     * 今日概览：预约按 slot_date=今日、排队按 ticket_date=今日，各一条 GROUP BY 查询。
     * storeId 为空表示全部门店（总部视角）。
     */
    public TodayOverviewVO todayOverview(Long storeId) {
        Map<Integer, Long> booking = toCountMap(bookingOrderMapper.countTodayByStatus(storeId));
        Map<Integer, Long> ticket = toCountMap(queueTicketMapper.countTodayByStatus(storeId));

        long bookingTotal = sum(booking);
        long ticketTotal = sum(ticket);
        return new TodayOverviewVO(
                LocalDate.now(),
                bookingTotal,
                count(booking, OrderStatus.PENDING_PAY.getCode()),
                count(booking, OrderStatus.PAID.getCode()),
                count(booking, OrderStatus.VERIFIED.getCode()),
                count(booking, OrderStatus.FINISHED.getCode()),
                count(booking, OrderStatus.NO_SHOW.getCode()),
                count(booking, OrderStatus.CANCELED.getCode()) + count(booking, OrderStatus.TIMEOUT_CANCELED.getCode()),
                ticketTotal,
                count(ticket, 0),   // 等待中
                count(ticket, 1),   // 叫号中
                count(ticket, 3),   // 已完成
                count(ticket, 4));  // 过号
    }

    /** 批量补用户手机号/昵称，避免列表每行查一次用户（N+1） */
    private Map<Long, SysUser> loadUsers(List<BookingOrder> orders) {
        List<Long> userIds = orders.stream().map(BookingOrder::getUserId).distinct().toList();
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
    }

    private AdminAppointmentVO toVO(BookingOrder order, SysUser user) {
        return new AdminAppointmentVO(
                order.getId(),
                order.getOrderNo(),
                order.getUserId(),
                user == null ? null : user.getPhone(),
                user == null ? null : user.getNickname(),
                order.getStoreId(),
                order.getServiceName(),
                order.getSlotDate(),
                order.getStartTime(),
                order.getEndTime(),
                order.getAmount(),
                order.getPaidAmount(),
                order.getStatus().getCode(),
                order.getStatus().getDesc(),
                order.getVerifyTime(),
                order.getCreatedAt());
    }

    private static Map<Integer, Long> toCountMap(List<StatusCount> rows) {
        return rows.stream().collect(Collectors.toMap(StatusCount::getStatus, StatusCount::getCnt));
    }

    private static long count(Map<Integer, Long> map, int status) {
        return map.getOrDefault(status, 0L);
    }

    private static long sum(Map<Integer, Long> map) {
        return map.values().stream().mapToLong(Long::longValue).sum();
    }
}

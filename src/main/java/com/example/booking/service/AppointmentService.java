package com.example.booking.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.booking.common.BizException;
import com.example.booking.common.CacheEvictSupport;
import com.example.booking.common.CacheNames;
import com.example.booking.common.PageResult;
import com.example.booking.common.ResultCode;
import com.example.booking.domain.BookingOrder;
import com.example.booking.domain.ServiceItem;
import com.example.booking.domain.TimeSlotStock;
import com.example.booking.domain.enums.OrderStatus;
import com.example.booking.dto.AppointmentVO;
import com.example.booking.dto.CreateAppointmentRequest;
import com.example.booking.event.AppointmentCreatedEvent;
import com.example.booking.mapper.BookingOrderMapper;
import com.example.booking.mapper.ServiceItemMapper;
import com.example.booking.mapper.TimeSlotStockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 预约下单服务。
 *
 * 事务边界（为什么这样切）：
 * 1) 同一事务：扣库存 + 写订单 + 发布领域事件。三者必须原子，否则会出现"扣了库存没订单"或"有订单没扣库存"。
 * 2) 事务外：MQ 发送、通知落库。用 AFTER_COMMIT 监听器触发，避免消息先于事务提交、事务回滚后产生脏消息。
 * 3) 幂等重查放在事务外：唯一索引冲突会让当前事务回滚，只有在事务结束后才能看到对方的已提交订单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    /** 支付窗口 15 分钟 */
    private static final int PAY_TIMEOUT_MINUTES = 15;
    private static final int IDEMPOTENT_KEY_MAX_LENGTH = 64;
    /** cancel_reason 列长度，超长截断而不是直接报错，避免用户输入把取消失败 */
    private static final int CANCEL_REASON_MAX_LENGTH = 255;
    private static final String DEFAULT_CANCEL_REASON = "用户主动取消";
    private static final int SLOT_STATUS_ON_SALE = 1;
    private static final int SERVICE_STATUS_ON_SHELF = 1;
    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BookingOrderMapper bookingOrderMapper;
    private final TimeSlotStockMapper timeSlotStockMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final CacheEvictSupport cacheEvictSupport;

    public AppointmentVO create(Long userId, String idempotencyKey, CreateAppointmentRequest request) {
        String key = validateIdempotencyKey(idempotencyKey);

        // 幂等第一层：先查。覆盖用户重复点击、网络重试，命中直接返回原订单，开销最小。
        BookingOrder existing = findByIdempotentKey(userId, key);
        if (existing != null) {
            log.info("幂等命中，直接返回已有订单 userId={} key={} orderNo={}", userId, key, existing.getOrderNo());
            return toVO(existing);
        }

        try {
            // 事务内只做数据库操作，事件在事务内发布、提交后才真正发 MQ
            return Objects.requireNonNull(transactionTemplate.execute(status -> doCreate(userId, key, request)));
        } catch (DuplicateKeyException e) {
            // 幂等第二层：唯一索引 uk_user_idempotent 兜底。
            // 并发时两个请求都通过第一层查询，只有一个能插入成功，另一个在这里被拦下。
            // 此时事务已回滚（库存已恢复），重新查一次拿对方已提交的订单返回即可。
            BookingOrder duplicated = findByIdempotentKey(userId, key);
            if (duplicated != null) {
                log.warn("并发重复下单被唯一索引拦下，返回已有订单 userId={} key={} orderNo={}",
                        userId, key, duplicated.getOrderNo());
                return toVO(duplicated);
            }
            throw e;
        }
    }

    /**
     * 顾客端「我的预约」。
     * 走 idx_user_status_created（user_id, status, created_at）：
     * 按用户过滤后主键倒序，分页深度也不会太大。
     */
    public PageResult<AppointmentVO> pageMine(Long userId, Integer status, long page, long size) {
        long pageNum = Math.max(1, page);
        long pageSize = Math.min(Math.max(1, size), 50);

        LambdaQueryWrapper<BookingOrder> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(BookingOrder::getUserId, userId);
        if (status != null) {
            OrderStatus orderStatus = OrderStatus.fromCode(status);
            if (orderStatus == null) {
                throw new BizException(ResultCode.PARAM_INVALID, "不支持的订单状态：" + status);
            }
            wrapper.eq(BookingOrder::getStatus, orderStatus);
        }
        wrapper.orderByDesc(BookingOrder::getId);

        Page<BookingOrder> result = bookingOrderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<AppointmentVO> records = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), result.getPages(), records);
    }

    private AppointmentVO doCreate(Long userId, String key, CreateAppointmentRequest request) {
        TimeSlotStock slot = timeSlotStockMapper.selectById(request.slotId());
        if (slot == null) {
            throw new BizException(ResultCode.SLOT_NOT_FOUND);
        }
        if (!Integer.valueOf(SLOT_STATUS_ON_SALE).equals(slot.getStatus())) {
            throw new BizException(ResultCode.SLOT_CLOSED);
        }
        if (!slot.getSlotDate().atTime(slot.getStartTime()).isAfter(LocalDateTime.now())) {
            throw new BizException(ResultCode.SLOT_CLOSED, "该时段已开始，无法预约");
        }

        ServiceItem service = serviceItemMapper.selectById(slot.getServiceId());
        if (service == null) {
            throw new BizException(ResultCode.SERVICE_NOT_FOUND);
        }
        if (!Integer.valueOf(SERVICE_STATUS_ON_SHELF).equals(service.getStatus())) {
            throw new BizException(ResultCode.SERVICE_OFF_SHELF);
        }

        // 条件更新扣库存：判断与自减在同一条 SQL，靠 InnoDB 行锁保证并发安全。
        // 返回 0 说明 remain_stock 已为 0 或时段被关闭，直接失败并回滚事务。
        int deducted = timeSlotStockMapper.deductOne(slot.getId());
        if (deducted == 0) {
            throw new BizException(ResultCode.SLOT_SOLD_OUT);
        }

        LocalDateTime now = LocalDateTime.now();
        BookingOrder order = new BookingOrder();
        order.setOrderNo(generateOrderNo());
        order.setIdempotentKey(key);
        order.setUserId(userId);
        order.setStoreId(slot.getStoreId());
        order.setServiceId(slot.getServiceId());
        order.setResourceId(slot.getResourceId());
        order.setSlotId(slot.getId());
        order.setSlotDate(slot.getSlotDate());
        order.setStartTime(slot.getStartTime());
        order.setEndTime(slot.getEndTime());
        order.setServiceName(service.getName());
        order.setAmount(service.getPrice());
        order.setStatus(OrderStatus.PENDING_PAY);
        order.setPayDeadline(now.plusMinutes(PAY_TIMEOUT_MINUTES));
        order.setRemark(request.remark());
        bookingOrderMapper.insert(order);

        // 事件在事务内发布，监听器在 AFTER_COMMIT 阶段才执行，保证"订单已提交"与"消息已发出"的顺序
        eventPublisher.publishEvent(new AppointmentCreatedEvent(
                order.getId(), order.getOrderNo(), userId,
                order.getPayDeadline().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));

        // 库存和订单同事务改完，提交后再删时段列表缓存（Cache Aside）。
        // 放在事务内注册 AFTER_COMMIT，而不是提交前删：提交前删会被并发读线程用旧库数据重新填回缓存。
        cacheEvictSupport.evictAfterCommit(CacheNames.SLOT_DAY, slotDayKey(slot.getStoreId(), slot.getServiceId(), slot.getSlotDate()));

        log.info("预约创建成功 userId={} orderNo={} slotId={} payDeadline={}",
                userId, order.getOrderNo(), slot.getId(), order.getPayDeadline());
        return toVO(order);
    }

    /**
     * 统一状态流转入口。
     * 其他模块（支付、核销、取消、超时关单）一律调用这里，不允许直接 update status，
     * 这样状态机规则只有一份，且 WHERE status = from 天然解决并发下的状态覆盖问题。
     */
    @Transactional
    public void changeStatus(Long orderId, OrderStatus from, OrderStatus to) {
        if (!from.canTransferTo(to)) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL,
                    String.format("订单状态 %s 不允许流转到 %s", from.getDesc(), to.getDesc()));
        }

        int updated = bookingOrderMapper.update(null, Wrappers.<BookingOrder>lambdaUpdate()
                .eq(BookingOrder::getId, orderId)
                .eq(BookingOrder::getStatus, from)
                .set(BookingOrder::getStatus, to)
                .set(BookingOrder::getUpdatedAt, LocalDateTime.now()));

        if (updated == 1) {
            // 取消/超时关单会让库存恢复，同样要在提交后删时段缓存
            if (to == OrderStatus.CANCELED || to == OrderStatus.TIMEOUT_CANCELED) {
                evictSlotCache(orderId);
            }
            return;
        }

        // 更新 0 行说明"订单不存在"或"状态已被别人改过"，查一次给出精确原因
        BookingOrder current = bookingOrderMapper.selectById(orderId);
        if (current == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL,
                String.format("订单当前状态为 %s，请刷新后重试", current.getStatus().getDesc()));
    }

    /**
     * 到店核销：已支付 -> 已核销。
     * 必须先校验订单存在再走状态机，避免 changeStatus 在“订单不存在”和“状态不对”之间给不出准确原因。
     */
    @Transactional
    public void verify(Long orderId) {
        BookingOrder order = bookingOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        changeStatus(orderId, OrderStatus.PAID, OrderStatus.VERIFIED);
        bookingOrderMapper.update(null, Wrappers.<BookingOrder>lambdaUpdate()
                .eq(BookingOrder::getId, orderId)
                .set(BookingOrder::getVerifyTime, LocalDateTime.now()));
        log.info("订单核销成功 orderId={} orderNo={}", orderId, order.getOrderNo());
    }

    /**
     * 爽约标记：已支付 -> 爽约。
     * 只允许从 PAID 流转，已核销/已完成/已取消的订单不能再标爽约（状态机保证幂等）。
     */
    @Transactional
    public void markNoShow(Long orderId) {
        BookingOrder order = bookingOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        changeStatus(orderId, OrderStatus.PAID, OrderStatus.NO_SHOW);
        log.info("订单标记爽约 orderId={} orderNo={}", orderId, order.getOrderNo());
    }

    /**
     * 顾客取消预约：待支付 -> 用户取消，并回滚库存。
     *
     * 三点设计：
     * 1) 归属校验：订单必须属于当前登录用户；不是本人订单一律返回"订单不存在"，
     *    避免通过错误信息差异探测他人订单号（IDOR）。
     * 2) 只允许待支付取消：已支付订单涉及退款，走 REFUNDING/REFUNDED 流程，
     *    这里不做"取消但不退钱"的假逻辑。
     * 3) 并发安全：changeStatus 用 WHERE status = PENDING_PAY 条件更新，
     *    与支付回调、超时关单并发时只有一方能成功；库存回滚与状态变更同事务，
     *    状态没改成功就整体回滚，保证库存只回滚一次。
     */
    @Transactional
    public void cancel(Long userId, Long orderId, String reason) {
        BookingOrder order = bookingOrderMapper.selectById(orderId);
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAY) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL,
                    String.format("订单当前状态为 %s，只有待支付订单可以取消", order.getStatus().getDesc()));
        }

        // 条件更新天然防并发：抢不到的一方会抛 ORDER_STATUS_ILLEGAL 并回滚，库存不会被多还
        changeStatus(orderId, OrderStatus.PENDING_PAY, OrderStatus.CANCELED);
        timeSlotStockMapper.restoreOne(order.getSlotId());

        bookingOrderMapper.update(null, Wrappers.<BookingOrder>lambdaUpdate()
                .eq(BookingOrder::getId, orderId)
                .set(BookingOrder::getCancelTime, LocalDateTime.now())
                .set(BookingOrder::getCancelReason, normalizeCancelReason(reason)));

        log.info("用户取消预约成功 userId={} orderId={} orderNo={} slotId={} reason={}",
                userId, orderId, order.getOrderNo(), order.getSlotId(), normalizeCancelReason(reason));
    }

    /** 取消原因：空值给默认文案，超长按列长度截断 */
    private String normalizeCancelReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return DEFAULT_CANCEL_REASON;
        }
        String trimmed = reason.trim();
        return trimmed.length() > CANCEL_REASON_MAX_LENGTH
                ? trimmed.substring(0, CANCEL_REASON_MAX_LENGTH)
                : trimmed;
    }

    /** 查 apply 幂等键对应的订单，冲突者共用 uk_user_idempotent 索引 */
    private BookingOrder findByIdempotentKey(Long userId, String key) {
        return bookingOrderMapper.selectOne(Wrappers.<BookingOrder>lambdaQuery()
                .eq(BookingOrder::getUserId, userId)
                .eq(BookingOrder::getIdempotentKey, key));
    }

    /** changeStatus 只知道订单 ID，回查一次拿缓存 key 需要的门店/服务/日期 */
    private void evictSlotCache(Long orderId) {
        BookingOrder order = bookingOrderMapper.selectById(orderId);
        if (order != null) {
            cacheEvictSupport.evictAfterCommit(CacheNames.SLOT_DAY,
                    slotDayKey(order.getStoreId(), order.getServiceId(), order.getSlotDate()));
        }
    }

    /** 与 SlotQueryService 的 @Cacheable key 表达式保持一致 */
    private String slotDayKey(Long storeId, Long serviceId, LocalDate slotDate) {
        return storeId + ":" + serviceId + ":" + slotDate;
    }

    private String validateIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BizException(ResultCode.PARAM_INVALID, "缺少幂等键 Idempotency-Key");
        }
        String key = idempotencyKey.trim();
        if (key.length() > IDEMPOTENT_KEY_MAX_LENGTH) {
            throw new BizException(ResultCode.PARAM_INVALID, "Idempotency-Key 最长 64 位");
        }
        return key;
    }

    /**
     * 单号 = BK + 秒级时间 + 8 位随机串。
     * 加随机串而不是纯时间戳：同一毫秒内 200 个并发请求，纯时间戳必然撞 uk_order_no。
     */
    private String generateOrderNo() {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "BK" + LocalDateTime.now().format(ORDER_NO_TIME) + random;
    }

    private AppointmentVO toVO(BookingOrder order) {
        return new AppointmentVO(
                order.getId(),
                order.getOrderNo(),
                order.getStatus().getCode(),
                order.getStatus().getDesc(),
                order.getAmount(),
                order.getSlotDate(),
                order.getStartTime(),
                order.getEndTime(),
                order.getServiceName(),
                order.getPayDeadline());
    }
}

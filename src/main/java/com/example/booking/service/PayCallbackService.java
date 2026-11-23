package com.example.booking.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.booking.common.BizException;
import com.example.booking.common.ResultCode;
import com.example.booking.domain.BookingOrder;
import com.example.booking.domain.PaymentRecord;
import com.example.booking.domain.enums.OrderStatus;
import com.example.booking.dto.PayCallbackRequest;
import com.example.booking.dto.PayCallbackVO;
import com.example.booking.event.OrderPaidEvent;
import com.example.booking.mapper.BookingOrderMapper;
import com.example.booking.mapper.PaymentRecordMapper;
import com.example.booking.mapper.UserPointMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * 支付回调处理。
 *
 * 幂等设计（两层）：
 * 1) trade_no 唯一索引 uk_trade_no：并发重复回调时只有一条流水能插入成功；
 * 2) 订单状态机条件更新 PENDING_PAY -> PAID：即使换了 trade_no（渠道重试生成新流水号），
 *    状态也只会被推进一次，积分、通知都挂在这条路径上，不会重复执行。
 *
 * 事务边界：验签在事务外（纯计算）；落流水、改状态、加积分在同一事务；
 * 通知消息通过 AFTER_COMMIT 监听器发送，订单没提交就不会有通知。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayCallbackService {

    private static final int PAY_STATUS_SUCCESS = 1;
    private static final int PAY_CHANNEL_MOCK = 1;
    private static final String CALLBACK_STATUS_SUCCESS = "SUCCESS";
    private static final DateTimeFormatter PAY_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BookingOrderMapper bookingOrderMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final UserPointMapper userPointMapper;
    private final AppointmentService appointmentService;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${pay.callback.secret}")
    private String signSecret;

    @Value("${pay.points-per-payment:10}")
    private int pointsPerPayment;

    public PayCallbackVO handle(PayCallbackRequest request) {
        verifySign(request);
        if (!CALLBACK_STATUS_SUCCESS.equalsIgnoreCase(request.status())) {
            throw new BizException(ResultCode.PAY_FAILED, "支付状态非成功，忽略本次回调");
        }

        // 幂等第一层（快速路径）：这笔三方交易号已经处理过，直接返回成功，不再做任何写入
        PaymentRecord existing = findByTradeNo(request.tradeNo());
        if (existing != null) {
            return duplicateResult(existing);
        }

        try {
            return Objects.requireNonNull(transactionTemplate.execute(status -> doHandle(request)));
        } catch (DuplicateKeyException e) {
            // 幂等第一层（并发路径）：两个回调同时进来，唯一索引只放一个。
            // 当前事务已回滚（流水没落、积分没加），在事务外重查对方已提交的流水，返回成功。
            PaymentRecord duplicated = findByTradeNo(request.tradeNo());
            if (duplicated != null) {
                return duplicateResult(duplicated);
            }
            throw e;
        }
    }

    /**
     * 模拟支付：前端点「立即支付」时调用。
     * 服务端按订单金额和固定密钥自己生成流水号与签名，再走与真实回调完全相同的
     * handle()，所以幂等、验签、加积分这些逻辑不会出现"只在模拟环境才走"的分叉。
     * 每次调用生成新 tradeNo：订单已支付时会被状态机拦下，返回成功但不重复加积分。
     */
    public PayCallbackVO mockPay(String orderNo) {
        BookingOrder order = bookingOrderMapper.selectOne(Wrappers.<BookingOrder>lambdaQuery()
                .eq(BookingOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }

        String tradeNo = "MOCK" + LocalDateTime.now().format(PAY_NO_TIME)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        String amount = order.getAmount().toPlainString();
        String sign = md5Hex(orderNo + "|" + tradeNo + "|" + amount + "|" + CALLBACK_STATUS_SUCCESS
                + "|" + signSecret);
        return handle(new PayCallbackRequest(orderNo, tradeNo, order.getAmount(),
                CALLBACK_STATUS_SUCCESS, sign));
    }

    private PayCallbackVO doHandle(PayCallbackRequest request) {
        BookingOrder order = bookingOrderMapper.selectOne(Wrappers.<BookingOrder>lambdaQuery()
                .eq(BookingOrder::getOrderNo, request.orderNo()));
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        if (order.getAmount().compareTo(request.amount()) != 0) {
            throw new BizException(ResultCode.PAY_AMOUNT_MISMATCH);
        }

        // 幂等第二层：状态已不是待支付，说明已经处理过（或订单已关闭）
        if (order.getStatus() != OrderStatus.PENDING_PAY) {
            if (isPaidStatus(order.getStatus())) {
                return new PayCallbackVO(order.getOrderNo(), request.tradeNo(),
                        order.getStatus().getCode(), order.getStatus().getDesc(), true);
            }
            throw new BizException(ResultCode.PAY_FAILED, "订单已关闭，无法支付");
        }

        LocalDateTime now = LocalDateTime.now();
        PaymentRecord record = new PaymentRecord();
        record.setPayNo(generatePayNo());
        record.setOrderId(order.getId());
        record.setOrderNo(order.getOrderNo());
        record.setUserId(order.getUserId());
        record.setChannel(PAY_CHANNEL_MOCK);
        record.setAmount(request.amount());
        record.setTradeNo(request.tradeNo());
        record.setStatus(PAY_STATUS_SUCCESS);
        record.setPaidAt(now);
        record.setRemark("模拟支付回调");
        paymentRecordMapper.insert(record);

        // 状态机条件更新：并发下只有一个回调能把 PENDING_PAY 推到 PAID，
        // 抢不到的一方在这里抛异常并整体回滚，不会重复加积分、重复发通知
        appointmentService.changeStatus(order.getId(), OrderStatus.PENDING_PAY, OrderStatus.PAID);

        bookingOrderMapper.update(null, Wrappers.<BookingOrder>lambdaUpdate()
                .eq(BookingOrder::getId, order.getId())
                .set(BookingOrder::getPaidAmount, request.amount())
                .set(BookingOrder::getPayTime, now));

        int added = userPointMapper.addPoints(order.getUserId(), pointsPerPayment);
        if (added == 0) {
            // 开发环境 X-User-Id 是占位用户，sys_user 里可能没有记录；生产环境应保证用户存在
            log.warn("积分累加未命中用户 userId={}", order.getUserId());
        }

        // 事务内发布，AFTER_COMMIT 后才投递通知，避免"通知已发、支付事务却回滚"
        eventPublisher.publishEvent(new OrderPaidEvent(
                order.getId(), order.getOrderNo(), order.getUserId(), request.amount(), request.tradeNo(), pointsPerPayment));

        log.info("支付回调成功 orderNo={} tradeNo={} points={}", order.getOrderNo(), request.tradeNo(), pointsPerPayment);
        return new PayCallbackVO(order.getOrderNo(), request.tradeNo(),
                OrderStatus.PAID.getCode(), OrderStatus.PAID.getDesc(), false);
    }

    /** 模拟验签：MD5(orderNo|tradeNo|amount|status|secret)，用常量时间比较防止时序攻击 */
    private void verifySign(PayCallbackRequest request) {
        String raw = request.orderNo() + "|" + request.tradeNo() + "|" + request.amount().toPlainString()
                + "|" + request.status() + "|" + signSecret;
        String expected = md5Hex(raw);
        boolean matched = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                request.sign().toUpperCase().getBytes(StandardCharsets.UTF_8));
        if (!matched) {
            log.warn("支付回调验签失败 orderNo={} tradeNo={}", request.orderNo(), request.tradeNo());
            throw new BizException(ResultCode.PAY_SIGN_INVALID);
        }
    }

    private static String md5Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().withUpperCase().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 算法不可用", e);
        }
    }

    private boolean isPaidStatus(OrderStatus status) {
        return status == OrderStatus.PAID || status == OrderStatus.VERIFIED || status == OrderStatus.FINISHED;
    }

    private PaymentRecord findByTradeNo(String tradeNo) {
        return paymentRecordMapper.selectOne(Wrappers.<PaymentRecord>lambdaQuery()
                .eq(PaymentRecord::getTradeNo, tradeNo));
    }

    private PayCallbackVO duplicateResult(PaymentRecord record) {
        log.info("重复支付回调，直接返回成功 tradeNo={} orderNo={}", record.getTradeNo(), record.getOrderNo());
        return new PayCallbackVO(record.getOrderNo(), record.getTradeNo(),
                OrderStatus.PAID.getCode(), "已支付（重复回调）", true);
    }

    private String generatePayNo() {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "PAY" + LocalDateTime.now().format(PAY_NO_TIME) + random;
    }
}

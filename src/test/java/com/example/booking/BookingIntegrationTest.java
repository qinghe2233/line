package com.example.booking;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.booking.common.BizException;
import com.example.booking.domain.BookingOrder;
import com.example.booking.domain.TimeSlotStock;
import com.example.booking.domain.enums.OrderStatus;
import com.example.booking.dto.CreateAppointmentRequest;
import com.example.booking.mapper.BookingOrderMapper;
import com.example.booking.mapper.TimeSlotStockMapper;
import com.example.booking.service.AppointmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 预约下单 3 个核心集成测试。
 *
 * 前置条件：本地 MySQL（端口 3306，root/123456）与 Redis（6379）已启动，
 *          并执行过 src/main/resources/db/schema.sql + seed.sql。
 * 运行：
 *   mvn -q test -Dtest=BookingIntegrationTest
 *
 * 注意：不加 @Transactional —— 每个测试方法自己开连接、自己提交/回滚。
 * 多线程子任务不能和父方法共享同一物理事务，否则在 RR 隔离级下
 * 子线程会读到未提交快照、看不到父线程刚改的数据。
 */
@SpringBootTest
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BookingIntegrationTest {

    private static final int CONCURRENCY = 50;       // 并发扣库存线程数
    private static final int STOCK = 5;              // 目标时段的初始名额
    private static final int IDEMPOTENT_THREADS = 30; // 幂等并发线程数
    private static final long TEST_USER_ID = 10001L; // seed.sql 里的小张（role=0）

    @Autowired AppointmentService appointmentService;
    @Autowired BookingOrderMapper bookingOrderMapper;
    @Autowired TimeSlotStockMapper timeSlotStockMapper;

    /** 测试用 slot —— 取 seed.sql 里未来最早的一个可售时段 */
    private Long slotId;

    /**
     * 每个测试方法开始前：确保有一个可售 slot（没有就造）、重置库存、清掉这个 slot 的历史订单。
     * 单独执行 SQL，不包事务，保证 commit 后再进入并发测试逻辑。
     */
    @BeforeEach
    void prepareSlot() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        // 取明天及以后的 slot，避免"今天的时段已开始"被拦截（create 会校验时段必须晚于 now）
        TimeSlotStock slot = timeSlotStockMapper.selectOne(Wrappers.<TimeSlotStock>lambdaQuery()
                .eq(TimeSlotStock::getStatus, 1)
                .ge(TimeSlotStock::getSlotDate, tomorrow)
                .orderByAsc(TimeSlotStock::getSlotDate, TimeSlotStock::getStartTime)
                .last("LIMIT 1"));
        if (slot == null) {
            slot = new TimeSlotStock();
            slot.setStoreId(1L);
            slot.setServiceId(1L);
            slot.setResourceId(1L);
            slot.setSlotDate(tomorrow.plusDays(1));
            slot.setStartTime(LocalTime.of(10, 0));
            slot.setEndTime(LocalTime.of(11, 0));
            slot.setTotalStock(STOCK);
            slot.setRemainStock(STOCK);
            slot.setStatus(1);
            timeSlotStockMapper.insert(slot);
        }
        slotId = slot.getId();
        resetSlotStock();
        bookingOrderMapper.delete(Wrappers.<BookingOrder>lambdaQuery()
                .eq(BookingOrder::getSlotId, slotId));
    }

    private void resetSlotStock() {
        timeSlotStockMapper.update(null, Wrappers.<TimeSlotStock>lambdaUpdate()
                .eq(TimeSlotStock::getId, slotId)
                .set(TimeSlotStock::getRemainStock, STOCK)
                .set(TimeSlotStock::getTotalStock, STOCK)
                .set(TimeSlotStock::getStatus, 1));
    }

    /**
     * 测试一：条件更新扣库存不会超卖。
     *
     * 50 个线程并发对同一个 slot 调 deductOne，expect:
     *   - 正好 STOCK 个成功（返回 affected=1），剩余全部返回 0
     *   - remain_stock 最终 = 0
     *
     * 核心机制：UPDATE ... WHERE remain_stock > 0 把判断和自减合成一条 SQL，
     * InnoDB 命中行后加 X 锁，后到的事务等前一事务提交后再判断。
     * 并发下虽然线程多，但最终只会扣掉 STOCK 个名额。
     */
    @Test
    @Order(1)
    @DisplayName("条件更新扣库存：并发不超卖")
    void deductOne_notOversold_underConcurrency() throws InterruptedException {
        int threads = CONCURRENCY;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                latch.countDown();
                try {
                    latch.await(); // 所有线程同时卡在这里，模拟真实并发竞争
                    int affected = timeSlotStockMapper.deductOne(slotId);
                    if (affected == 1) success.incrementAndGet();
                    else soldOut.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        pool.shutdown();
        boolean done = pool.awaitTermination(15, TimeUnit.SECONDS);
        assertThat(done).as("线程池应该在超时前结束").isTrue();

        TimeSlotStock after = timeSlotStockMapper.selectById(slotId);
        assertThat(success.get()).isEqualTo(STOCK);
        assertThat(soldOut.get()).isEqualTo(threads - STOCK);
        assertThat(after).isNotNull();
        assertThat(after.getRemainStock()).isEqualTo(0);
    }

    /**
     * 测试二：同一幂等键重复提交只产生一张订单。
     *
     * 30 个线程用同一 userId + 同一 Idempotency-Key 并发调 create，expect:
     *   - 全部返回成功（code=0）
     *   - 数据库里只有 1 个 order_no
     *   - 库存只扣 1
     *
     * 幂等两层：
     * 1) 先查 findByIdempotentKey —— 大部分线程在这里命中第一个线程已提交的订单
     * 2) uk_user_idempotent 唯一索引兜底 —— 并发下两个线程同时过了第一层
     *    时只有一个能 insert 成功，另一个 catch DuplicateKeyException 后重查返回
     *    对方已提交的订单。库存在事务回滚后自动恢复，所以最终只扣 1。
     */
    @Test
    @Order(2)
    @DisplayName("幂等下单：同键只产一张订单")
    void create_idempotentKey_onlyOneOrder() throws InterruptedException {
        String idemKey = "junit-idem-" + System.currentTimeMillis();
        int threads = IDEMPOTENT_THREADS;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<String> orderNos = Collections.synchronizedList(new ArrayList<>());

        CreateAppointmentRequest req = new CreateAppointmentRequest(slotId, "幂等测试");

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                latch.countDown();
                try {
                    latch.await();
                    var vo = appointmentService.create(TEST_USER_ID, idemKey, req);
                    success.incrementAndGet();
                    orderNos.add(vo.orderNo());
                } catch (Exception e) {
                    failed.incrementAndGet();
                }
            });
        }
        pool.shutdown();
        boolean done = pool.awaitTermination(30, TimeUnit.SECONDS);
        assertThat(done).as("线程池应该在超时前结束").isTrue();

        // 全部返回成功 —— 幂等在第一次命中后所有后续调用都直接返回同一张订单
        assertThat(success.get()).isEqualTo(threads);
        assertThat(failed.get()).isEqualTo(0);

        // 订单号完全相同 —— 幂等生效，不会产生新订单
        assertThat(orderNos).isNotEmpty();
        String firstOrderNo = orderNos.get(0);
        assertThat(orderNos).allMatch(no -> no.equals(firstOrderNo));

        // 数据库里确实只有一条 booking_order
        long actual = bookingOrderMapper.selectCount(Wrappers.<BookingOrder>lambdaQuery()
                .eq(BookingOrder::getIdempotentKey, idemKey)
                .eq(BookingOrder::getUserId, TEST_USER_ID));
        assertThat(actual).isEqualTo(1);

        // 库存只扣 1 —— 被唯一索引拦下的线程回滚后库存自动恢复
        TimeSlotStock after = timeSlotStockMapper.selectById(slotId);
        assertThat(after).isNotNull();
        assertThat(after.getRemainStock()).isEqualTo(STOCK - 1);
    }

    /**
     * 测试三：状态机拒绝非法流转。
     *
     * VERIFIED（已核销，终态链里可以转 FINISHED）→ PENDING_PAY（回到待支付）
     * 在状态机里没有定义，changeStatus 内部 canTransferTo 返回 false，
     * 直接抛 BizException（ORDER_STATUS_ILLEGAL，40006）。
     *
     * 这比"非法操作"更具体 —— 面试时能讲清楚状态机怎么防止
     * 把一张已核销的订单改回"待支付"去骗取退款。
     * 同时 VERIFIED → FINISHED 的合法流转要能成功，正反都测一遍。
     */
    @Test
    @Order(3)
    @DisplayName("状态机非法流转：VERIFIED → PENDING_PAY 被拒绝")
    void changeStatus_illegalTransition_throwsBizException() {
        // order_no 列 VARCHAR(32)，前缀 + 随机数控制长度
        String orderNo = "SM-" + Long.toHexString(System.nanoTime());
        BookingOrder order = new BookingOrder();
        order.setOrderNo(orderNo);
        order.setIdempotentKey("idem-sm-" + System.currentTimeMillis());
        order.setUserId(TEST_USER_ID);
        order.setStoreId(1L);
        order.setServiceId(1L);
        order.setResourceId(1L);
        order.setSlotId(slotId);
        order.setSlotDate(LocalDate.now());
        order.setStartTime(LocalTime.of(10, 0));
        order.setEndTime(LocalTime.of(11, 0));
        order.setServiceName("测试服务");
        order.setAmount(BigDecimal.valueOf(88));
        order.setStatus(OrderStatus.VERIFIED);
        // pay_deadline NOT NULL，手工造订单要显式填；真实下单由 create 方法填 now+15min
        order.setPayDeadline(LocalDateTime.now().plusMinutes(15));
        bookingOrderMapper.insert(order);

        // 非法：VERIFIED → PENDING_PAY 没在状态机 TRANSITIONS 里
        assertThatThrownBy(() -> appointmentService.changeStatus(order.getId(),
                OrderStatus.VERIFIED, OrderStatus.PENDING_PAY))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException be = (BizException) ex;
                    // ORDER_STATUS_ILLEGAL = 40006
                    assertThat(be.getCode()).isEqualTo(40006);
                });

        // 合法：VERIFIED → FINISHED 应该成功
        appointmentService.changeStatus(order.getId(),
                OrderStatus.VERIFIED, OrderStatus.FINISHED);

        BookingOrder after = bookingOrderMapper.selectById(order.getId());
        assertThat(after).isNotNull();
        assertThat(after.getStatus()).isEqualTo(OrderStatus.FINISHED);

        // 清理：测试数据不回滚（没加 @Transactional），手工删掉
        bookingOrderMapper.deleteById(order.getId());
    }
}

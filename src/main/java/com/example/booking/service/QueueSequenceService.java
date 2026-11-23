package com.example.booking.service;

import com.example.booking.common.BizException;
import com.example.booking.common.ResultCode;
import com.example.booking.mapper.QueueTicketMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 排队序号生成：Redis INCR。
 *
 * 并发下不重号的原理：
 * 1) Redis 单线程执行命令，INCR 是原子操作，N 个并发请求一定拿到 N 个不同的递增值；
 * 2) key 按「门店 + 服务项 + 日期」分桶，不同分组互不影响；
 * 3) 数据库 uk_store_service_date_seq 是最外层保险：即使 Redis 被清空/回滚导致重号，
 *    插入时会被唯一索引拦下，由调用方重新取号（见 QueueTicketService 的重试）。
 *
 * 冷启动补偿：Redis 重启/被清空后当天第一次 INCR 会返回 1，
 * 这时用数据库 MAX(seq_no) 做种子（+1），避免从 1 开始一路撞唯一索引。
 * TTL 设为 2 天，过期后 key 自然清理，不需要定时删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueSequenceService {

    private static final String KEY_PREFIX = "booking:queue:seq:";
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long KEY_TTL_SECONDS = 2L * 24 * 3600;

    /** 自增并续期，返回自增后的值 */
    private static final DefaultRedisScript<Long> INCR_SCRIPT = new DefaultRedisScript<>(
            "local cur = redis.call('INCR', KEYS[1]) "
                    + "redis.call('EXPIRE', KEYS[1], ARGV[1]) "
                    + "return cur",
            Long.class);

    /** 冷启动时用 DB 最大值 + 1 覆盖计数器，并返回该值 */
    private static final DefaultRedisScript<Long> SEED_SCRIPT = new DefaultRedisScript<>(
            "redis.call('SET', KEYS[1], ARGV[1]) "
                    + "redis.call('EXPIRE', KEYS[1], ARGV[2]) "
                    + "return tonumber(ARGV[1])",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final QueueTicketMapper queueTicketMapper;

    public long nextSeq(Long storeId, Long serviceId, LocalDate ticketDate) {
        String key = buildKey(storeId, serviceId, ticketDate);
        try {
            Long seq = redisTemplate.execute(INCR_SCRIPT, List.of(key), String.valueOf(KEY_TTL_SECONDS));
            if (seq == null) {
                throw new BizException(ResultCode.SYSTEM_ERROR, "取号序号生成失败");
            }
            if (seq == 1L) {
                // 当天第一个号：可能是正常首号，也可能是 Redis 丢数据后重新计数，统一用 DB 最大值校正
                long dbMax = queueTicketMapper.selectMaxSeq(storeId, serviceId, ticketDate);
                if (dbMax > 0) {
                    Long seeded = redisTemplate.execute(SEED_SCRIPT, List.of(key),
                            String.valueOf(dbMax + 1), String.valueOf(KEY_TTL_SECONDS));
                    long result = seeded == null ? dbMax + 1 : seeded;
                    log.warn("排队序号冷启动校正 storeId={} serviceId={} date={} dbMax={} next={}",
                            storeId, serviceId, ticketDate, dbMax, result);
                    return result;
                }
            }
            return seq;
        } catch (DataAccessException e) {
            // Redis 不可用时宁可取号失败，也不能退化成 DB MAX+1 让并发请求大规模撞唯一索引
            log.error("取号序号生成失败，Redis 不可用 storeId={} serviceId={}", storeId, serviceId, e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "取号服务暂不可用，请稍后重试");
        }
    }

    private String buildKey(Long storeId, Long serviceId, LocalDate ticketDate) {
        return KEY_PREFIX + storeId + ":" + serviceId + ":" + ticketDate.format(DAY_FORMAT);
    }
}

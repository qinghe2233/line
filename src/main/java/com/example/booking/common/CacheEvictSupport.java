package com.example.booking.common;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 缓存删除工具（Cache Aside 的"删"这一半）。
 *
 * 策略：先更新数据库，事务提交后再删缓存，绝不"更新缓存"。
 * 删失败的处理分两层：
 * 1) 立即重试 3 次（间隔 50ms）：覆盖 Redis 抖动、连接池耗尽等瞬时故障；
 * 2) 延时 500ms 再删一次（延时双删）：覆盖"另一个线程在删之前读到旧库、删之后把旧值写回缓存"的竞态。
 * 仍失败则只记日志，由 60 秒~5 分钟 的短 TTL 兜底——缓存只服务展示，允许最终一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictSupport {

    private static final int MAX_RETRY = 3;
    private static final long RETRY_INTERVAL_MILLIS = 50L;
    private static final long DOUBLE_DELETE_DELAY_MILLIS = 500L;

    private final CacheManager cacheManager;

    /** 单线程足够：双删只是"补一刀"，不追求吞吐，也不允许它拖慢业务线程 */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cache-double-delete");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 事务提交后删除。没有事务时立即删除。
     * 必须放在提交后：如果提交前删，别的线程可能读到旧库数据又写回缓存，导致缓存长期是旧值。
     */
    public void evictAfterCommit(String cacheName, String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictWithDelayedDoubleDelete(cacheName, key);
                }
            });
        } else {
            evictWithDelayedDoubleDelete(cacheName, key);
        }
    }

    /** 立即删 + 延时 500ms 再删 */
    public void evictWithDelayedDoubleDelete(String cacheName, String key) {
        evictWithRetry(cacheName, key);
        scheduler.schedule(() -> evictWithRetry(cacheName, key),
                DOUBLE_DELETE_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void evictWithRetry(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("缓存不存在，跳过删除 cache={} key={}", cacheName, key);
            return;
        }
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                cache.evict(key);
                return;
            } catch (Exception e) {
                if (attempt == MAX_RETRY) {
                    // 重试仍失败：不抛给业务，交给 TTL 过期兜底
                    log.error("缓存删除最终失败，等待 TTL 过期兜底 cache={} key={}", cacheName, key, e);
                } else {
                    sleepQuietly();
                }
            }
        }
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(RETRY_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }
}

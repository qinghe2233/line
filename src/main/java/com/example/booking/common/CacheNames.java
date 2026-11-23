package com.example.booking.common;

/**
 * 缓存名称与 Key 规范。
 *
 * Redis 实际 Key = booking:cache:v1:{cacheName}:{业务key}，例如：
 *   门店详情      booking:cache:v1:store:detail:1
 *   某天可约时段  booking:cache:v1:slot:day:1:2:2026-11-22
 *
 * 为什么带 v1 版本号：
 * 需求变更导致缓存结构不兼容时，把前缀改成 v2 即可整体失效，
 * 不需要用 KEYS/SCAN 扫全库删除（KEYS 会阻塞 Redis 主线程）。
 */
public final class CacheNames {

    /** 门店详情：{storeId} */
    public static final String STORE_DETAIL = "store:detail";

    /** 某天可约时段：{storeId}:{serviceId}:{yyyy-MM-dd} */
    public static final String SLOT_DAY = "slot:day";

    private CacheNames() {
    }
}

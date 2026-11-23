package com.example.booking.dto;

import java.time.LocalTime;

/**
 * 门店详情。门店名称、地址、营业时间变化频率极低，适合缓存。
 */
public record StoreDetailVO(
        Long storeId,
        String name,
        String city,
        String address,
        String phone,
        LocalTime businessStart,
        LocalTime businessEnd,
        Integer status) {
}

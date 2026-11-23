package com.example.booking.dto;

import com.example.booking.domain.ServiceItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** 服务项视图，管理端与顾客端共用（顾客端只取 status=1 的记录）。 */
@Schema(description = "服务项")
public record ServiceItemVO(
        Long id,
        Long storeId,
        String name,
        Integer durationMinutes,
        Integer bufferMinutes,
        BigDecimal price,
        String description,
        Integer sortNo,
        Integer status
) {

    public static ServiceItemVO of(ServiceItem item) {
        return new ServiceItemVO(
                item.getId(),
                item.getStoreId(),
                item.getName(),
                item.getDurationMinutes(),
                item.getBufferMinutes(),
                item.getPrice(),
                item.getDescription(),
                item.getSortNo(),
                item.getStatus());
    }
}

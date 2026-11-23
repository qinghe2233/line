package com.example.booking.dto;

import lombok.Data;

import java.time.LocalTime;

/**
 * 某天可约时段（列表元素）。
 * 用普通类而不是 record：MyBatis 默认按无参构造 + setter 映射结果集，
 * record 需要额外开启 argNameBasedConstructorAutoMapping，没必要为 6 个字段改全局配置。
 * 注意：这里的 remainStock 是缓存快照，只能用于展示，下单时以 DB 条件扣减结果为准。
 */
@Data
public class AvailableSlotVO {

    private Long slotId;
    private Long resourceId;
    private String resourceName;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer remainStock;
}

package com.example.booking.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 时段库存实体。
 * remain_stock 是热点字段，只通过 SQL 原子自减更新，不在 Java 里"先查再算再写"。
 */
@Data
@TableName("time_slot_stock")
public class TimeSlotStock {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long storeId;
    private Long resourceId;
    private Long serviceId;

    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;

    private Integer totalStock;
    private Integer remainStock;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

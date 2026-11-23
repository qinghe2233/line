package com.example.booking.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 服务项实体（只读使用，管理端后续补充）。
 */
@Data
@TableName("service_item")
public class ServiceItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long storeId;
    private String name;
    private Integer durationMinutes;
    private Integer bufferMinutes;
    private BigDecimal price;
    private String description;
    private Integer sortNo;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 全局逻辑删除字段，查询时 MyBatis-Plus 自动追加 deleted = 0 */
    private Integer deleted;
}

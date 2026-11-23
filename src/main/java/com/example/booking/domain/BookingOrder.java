package com.example.booking.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.booking.domain.enums.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 预约单实体。
 * 注意：没有 deleted 字段——订单不允许逻辑删除，只能靠状态机推进到终态。
 */
@Data
@TableName("booking_order")
public class BookingOrder {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private String idempotentKey;
    private Long userId;
    private Long storeId;
    private Long serviceId;
    private Long resourceId;
    private Long slotId;

    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String serviceName;

    private BigDecimal amount;
    private BigDecimal paidAmount;
    private BigDecimal refundAmount;
    private BigDecimal cancelFee;

    private OrderStatus status;

    private LocalDateTime payDeadline;
    private LocalDateTime payTime;
    private LocalDateTime verifyTime;
    private LocalDateTime finishTime;
    private LocalDateTime cancelTime;
    private String cancelReason;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

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
 * 支付流水。
 * uk_trade_no 是支付回调幂等的第一道防线：同一笔三方交易号只能落一条记录。
 */
@Data
@TableName("payment_record")
public class PaymentRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String payNo;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private Integer channel;
    private BigDecimal amount;
    private String tradeNo;
    private Integer status;
    private LocalDateTime paidAt;
    private BigDecimal refundAmount;
    private LocalDateTime refundAt;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

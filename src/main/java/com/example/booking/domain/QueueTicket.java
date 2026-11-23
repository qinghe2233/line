package com.example.booking.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.booking.domain.enums.TicketStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 排队号实体。
 * 号码规则：ticket_no = 服务项字母 + 3 位补齐序号，例如 A001；
 * seq_no 按「门店 + 服务项 + 当日」从 1 递增，ticket_no 与 seq_no 都有唯一索引兜底。
 */
@Data
@TableName("queue_ticket")
public class QueueTicket {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String ticketNo;
    private Integer seqNo;
    private Long storeId;
    private Long userId;
    private Long serviceId;
    private Long bookingOrderId;
    private LocalDate ticketDate;

    private TicketStatus status;

    private Long callStaffId;
    private LocalDateTime calledAt;
    private LocalDateTime finishedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

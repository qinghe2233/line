package com.example.booking.dto;

import lombok.Data;

/**
 * 「按状态分组计数」的通用行对象。
 * 今日概览用两条 GROUP BY 查询（预约单按状态、排队号按状态）代替 N 条 COUNT，
 * 避免每加一个指标就多一次数据库往返。
 */
@Data
public class StatusCount {

    private Integer status;
    private Long cnt;
}

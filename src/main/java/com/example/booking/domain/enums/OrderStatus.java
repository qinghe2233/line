package com.example.booking.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 预约单状态机。
 * 用枚举而不是 int 常量：非法流转在编译期/运行期都能被 canTransferTo 拦住，
 * 数据库里仍然只存数字（@EnumValue），不影响索引与查询。
 */
@Getter
public enum OrderStatus {

    PENDING_PAY(0, "待支付"),
    PAID(10, "已支付"),
    VERIFIED(20, "已核销"),
    FINISHED(30, "已完成"),
    NO_SHOW(35, "爽约"),
    CANCELED(40, "用户取消"),
    TIMEOUT_CANCELED(50, "超时取消"),
    REFUNDING(60, "退款中"),
    REFUNDED(70, "已退款");

    /** 存库用的值，MyBatis-Plus 自动在枚举与 int 之间转换 */
    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;

    /**
     * 允许的流转方向。只写"能去哪里"，没列出的组合一律拒绝，
     * 新增状态时必须显式补进这张表，避免漏改。
     */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            PENDING_PAY, EnumSet.of(PAID, CANCELED, TIMEOUT_CANCELED),
            PAID, EnumSet.of(VERIFIED, NO_SHOW, CANCELED, REFUNDING),
            VERIFIED, EnumSet.of(FINISHED),
            REFUNDING, EnumSet.of(REFUNDED),
            FINISHED, EnumSet.noneOf(OrderStatus.class),
            NO_SHOW, EnumSet.noneOf(OrderStatus.class),
            CANCELED, EnumSet.noneOf(OrderStatus.class),
            TIMEOUT_CANCELED, EnumSet.noneOf(OrderStatus.class),
            REFUNDED, EnumSet.noneOf(OrderStatus.class)
    );

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public boolean canTransferTo(OrderStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTerminal() {
        return TRANSITIONS.getOrDefault(this, Set.of()).isEmpty();
    }

    /** 按存库值反解枚举，用于管理端筛选参数与统计结果映射 */
    public static OrderStatus fromCode(int code) {
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}

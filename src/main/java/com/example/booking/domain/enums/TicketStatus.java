package com.example.booking.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 排队号状态机。
 *
 * 0-等待中 → 1-叫号中 → 3-已完成
 *                    ↘ 4-过号（叫号后 3 分钟未到，自动流转）
 *
 * 数据库注释里还预留了 2-服务中，本项目不做「到店入座」这一步，
 * 直接由店员在服务完成后把「叫号中」推进到「已完成」，因此不产生状态 2。
 * 所有状态变更都走带 WHERE status = from 的条件更新，保证并发下只成功一次。
 */
@Getter
public enum TicketStatus {

    WAITING(0, "等待中"),
    CALLING(1, "叫号中"),
    FINISHED(3, "已完成"),
    PASSED(4, "过号");

    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;

    /** 只允许列出的流转方向；FINISHED / PASSED 是终态 */
    private static final Map<TicketStatus, Set<TicketStatus>> TRANSITIONS = Map.of(
            WAITING, EnumSet.of(CALLING),
            CALLING, EnumSet.of(FINISHED, PASSED),
            FINISHED, EnumSet.noneOf(TicketStatus.class),
            PASSED, EnumSet.noneOf(TicketStatus.class)
    );

    TicketStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public boolean canTransferTo(TicketStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /** 按存库值反解枚举，用于管理端按状态筛选 */
    public static TicketStatus fromCode(int code) {
        for (TicketStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}

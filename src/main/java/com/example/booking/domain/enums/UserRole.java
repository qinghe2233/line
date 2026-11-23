package com.example.booking.domain.enums;

import lombok.Getter;

/**
 * 用户角色。
 * 角色值同时写进 JWT，过滤器据此生成 ROLE_xxx 权限；
 * 管理端接口只要求 STAFF / ADMIN，顾客访问会拿到 403。
 */
@Getter
public enum UserRole {

    CUSTOMER(0, "顾客", "ROLE_CUSTOMER"),
    STAFF(1, "店员", "ROLE_STAFF"),
    ADMIN(2, "店长", "ROLE_ADMIN");

    private final int code;
    private final String desc;
    private final String authority;

    UserRole(int code, String desc, String authority) {
        this.code = code;
        this.desc = desc;
        this.authority = authority;
    }

    /** 未知角色返回 null，由调用方按"无权限"处理，避免默认放大权限 */
    public static UserRole fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (UserRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        return null;
    }

    public static String descOf(Integer code) {
        UserRole role = fromCode(code);
        return role == null ? "未知角色" : role.getDesc();
    }
}

package com.example.booking.domain;

import lombok.Data;

/**
 * 登录校验专用行（含密码哈希）。
 * 单独建类而不是给 SysUser 加 password 字段：
 * SysUser 会在别处被查询/序列化，密码哈希不该有机会混进普通查询结果。
 */
@Data
public class SysUserAuth {

    private Long id;
    private String phone;
    private String nickname;
    private String password;
    private Integer role;
    private Long storeId;
    private Integer status;
}

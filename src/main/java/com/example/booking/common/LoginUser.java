package com.example.booking.common;

/**
 * 登录用户（JWT 解析后的 principal）。
 * 只放鉴权与业务需要的最小字段，不放密码等敏感信息；
 * Controller 通过 UserContext 拿到它，不再信任任何客户端传来的用户 ID。
 */
public record LoginUser(
        Long id,
        String phone,
        String nickname,
        Integer role,
        Long storeId
) {
}

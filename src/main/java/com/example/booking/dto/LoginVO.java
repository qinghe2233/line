package com.example.booking.dto;

/**
 * 登录结果。
 * token 之外把角色与门店带回去：前端据此决定进管理端还是顾客端，
 * 不必再解一遍 JWT 或在本地猜角色。
 */
public record LoginVO(
        String token,
        String tokenType,
        long expiresIn,
        Long userId,
        String phone,
        String nickname,
        Integer role,
        String roleName,
        Long storeId
) {
}

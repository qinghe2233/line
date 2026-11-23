package com.example.booking.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户上下文。
 *
 * 身份唯一来源是 SecurityContext：JwtAuthenticationFilter 校验签名并写入，
 * 这里只负责读取。请求头 / Cookie 里的任何 user id 都不再被信任。
 */
public final class UserContext {

    private UserContext() {
    }

    public static LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser user)) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long currentUserId() {
        return currentUser().id();
    }
}

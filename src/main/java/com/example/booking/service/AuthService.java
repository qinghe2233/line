package com.example.booking.service;

import com.example.booking.common.BizException;
import com.example.booking.common.LoginUser;
import com.example.booking.common.ResultCode;
import com.example.booking.domain.SysUserAuth;
import com.example.booking.domain.enums.UserRole;
import com.example.booking.dto.LoginRequest;
import com.example.booking.dto.LoginVO;
import com.example.booking.mapper.SysUserMapper;
import com.example.booking.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录认证。
 *
 * 两个安全细节：
 * 1) 用户不存在与密码错误返回同一个错误码，避免通过错误文案枚举手机号；
 * 2) 密码只以 BCrypt 哈希存库，由 PasswordEncoder.matches 做常量时间比对。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int USER_STATUS_NORMAL = 1;

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginVO login(LoginRequest request) {
        SysUserAuth user = sysUserMapper.selectAuthByPhone(request.phone().trim());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BizException(ResultCode.USER_PASSWORD_ERROR);
        }
        if (user.getStatus() == null || user.getStatus() != USER_STATUS_NORMAL) {
            throw new BizException(ResultCode.USER_DISABLED);
        }

        LoginUser loginUser = new LoginUser(user.getId(), user.getPhone(), user.getNickname(),
                user.getRole(), user.getStoreId());
        String token = jwtTokenProvider.generate(loginUser);
        log.info("用户登录成功 userId={} role={}", user.getId(), user.getRole());
        return toVO(loginUser, token);
    }

    /** /api/auth/me：返回当前登录态，前端刷新用户信息时使用，不重新签发 token */
    public LoginVO current(LoginUser user) {
        return toVO(user, null);
    }

    private LoginVO toVO(LoginUser user, String token) {
        return new LoginVO(
                token,
                "Bearer",
                token == null ? 0 : jwtTokenProvider.expireSeconds(),
                user.id(),
                user.phone(),
                user.nickname(),
                user.role(),
                UserRole.descOf(user.role()),
                user.storeId());
    }
}

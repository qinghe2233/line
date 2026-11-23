package com.example.booking.controller;

import com.example.booking.common.Result;
import com.example.booking.common.UserContext;
import com.example.booking.dto.LoginRequest;
import com.example.booking.dto.LoginVO;
import com.example.booking.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 认证接口：登录是唯一公开入口，其余接口都要求携带 JWT */
@Tag(name = "00-认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录", description = "手机号 + 密码，成功返回 JWT")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @Operation(summary = "当前登录用户", description = "调试与前端刷新用户信息用")
    @GetMapping("/me")
    public Result<LoginVO> me() {
        return Result.success(authService.current(UserContext.currentUser()));
    }
}

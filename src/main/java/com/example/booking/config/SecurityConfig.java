package com.example.booking.config;

import com.example.booking.common.Result;
import com.example.booking.common.ResultCode;
import com.example.booking.security.JwtAuthenticationFilter;
import com.example.booking.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Spring Security 配置：无状态 JWT 认证 + 按角色授权。
 *
 * 白名单只保留"不需要身份也能访问"的接口：
 * 登录、门店浏览、支付渠道回调、接口文档、健康检查。
 * 预约/排队要求任意登录用户，管理端要求 STAFF 或 ADMIN。
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] WHITE_LIST = {
            // 登录本身必须公开，否则拿不到 token
            "/api/auth/login",
            // 门店/服务/时段是浏览型数据，登录前也要能看
            "/api/stores/**",
            // 支付渠道回调没有用户登录态，靠验签保证来源可信
            "/api/pay/callback",
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/actuator/**",
            "/favicon.ico",
            "/error"
    };

    private final JwtTokenProvider jwtTokenProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
                // 前后端分离 + JWT，不存在 Cookie 会话，CSRF 防护无意义
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(WHITE_LIST).permitAll()
                        // 管理端只允许店员/店长；顾客拿着合法 token 也进不来
                        .requestMatchers("/api/admin/**").hasAnyRole("STAFF", "ADMIN")
                        // 预约、支付（模拟）、排队取号都要求登录，用户身份从 JWT 取
                        .anyRequest().authenticated())
                // 过滤器只解析 token，不拦截；未认证的受保护请求由 entry point 统一返回 401
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class)
                // 未登录/无权限也返回统一 Result，前端不需要为 401/403 单独写解析逻辑
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeJson(response, objectMapper, HttpStatus.UNAUTHORIZED,
                                        Result.fail(ResultCode.UNAUTHORIZED)))
                        .accessDeniedHandler((request, response, e) ->
                                writeJson(response, objectMapper, HttpStatus.FORBIDDEN,
                                        Result.fail(ResultCode.FORBIDDEN))));
        return http.build();
    }

    /** 密码只存 BCrypt 哈希，登录时用 matches 做常量时间比对 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 开发期放开跨域，方便 Vue 前端本地联调。
     * 用 allowedOriginPatterns 而不是 allowedOrigins("*")，
     * 因为后者与 allowCredentials(true) 不能共存。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static void writeJson(HttpServletResponse response, ObjectMapper objectMapper,
                                  HttpStatus status, Result<Void> body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}

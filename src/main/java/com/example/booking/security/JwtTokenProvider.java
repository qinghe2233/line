package com.example.booking.security;

import com.example.booking.common.LoginUser;
import com.example.booking.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 生成与解析。
 *
 * 只放必要的身份声明（userId/role/storeId），不放权限明细：
 * 角色是粗粒度的，权限变化时重新登录即可，不需要把整张权限表塞进 token。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String CLAIM_PHONE = "phone";
    private static final String CLAIM_NICKNAME = "nickname";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_STORE_ID = "storeId";

    private final JwtProperties properties;

    private SecretKey key;

    @PostConstruct
    void init() {
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generate(LoginUser user) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(expireSeconds());
        return Jwts.builder()
                .subject(String.valueOf(user.id()))
                .claim(CLAIM_PHONE, user.phone())
                .claim(CLAIM_NICKNAME, user.nickname())
                .claim(CLAIM_ROLE, user.role())
                .claim(CLAIM_STORE_ID, user.storeId())
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(key)
                .compact();
    }

    /**
     * 解析失败一律返回 null（过期、签名错误、格式错误都归为未认证），
     * 由 Security 的 entry point 统一输出 401，避免把异常细节暴露给客户端。
     */
    public LoginUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Number role = claims.get(CLAIM_ROLE, Number.class);
            Number storeId = claims.get(CLAIM_STORE_ID, Number.class);
            return new LoginUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_PHONE, String.class),
                    claims.get(CLAIM_NICKNAME, String.class),
                    role == null ? null : role.intValue(),
                    storeId == null ? null : storeId.longValue());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    public long expireSeconds() {
        return properties.getExpireMinutes() * 60;
    }
}

package com.example.booking.config;

import com.example.booking.common.CacheNames;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.NullValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spring Cache + Redis 配置。
 *
 * 三个保护都在这一层落地：
 * 1) 穿透：默认允许缓存 null（cacheNullValues 默认开启），null 由 TtlFunction 单独给 60 秒；
 * 2) 击穿：业务方法上 @Cacheable(sync = true)，未命中时用本地锁串行重建（单机够用，
 *    多实例要么 Redis SETNX，要么逻辑过期，本项目按单机方案讲清上限，不引入分布式锁）；
 * 3) 雪崩：TTL = 基础时长 + 0~N 秒随机，避免同一时刻批量过期；
 *    另外每个缓存名用不同基础 TTL，进一步打散。
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    /** 统一前缀，v1 是缓存结构版本号，升级结构时改成 v2 即可整体失效 */
    private static final String KEY_PREFIX = "booking:cache:v1:";

    private static final Duration NULL_VALUE_TTL = Duration.ofSeconds(60);
    private static final Duration STORE_DETAIL_BASE_TTL = Duration.ofMinutes(30);
    private static final int STORE_DETAIL_TTL_JITTER_SECONDS = 300;
    private static final Duration SLOT_DAY_BASE_TTL = Duration.ofMinutes(5);
    private static final int SLOT_DAY_TTL_JITTER_SECONDS = 60;

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                // 前缀由 CacheManager 统一拼接，业务代码只关心 cacheName + 业务 key
                .computePrefixWith(cacheName -> KEY_PREFIX + cacheName + ":")
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer()))
                .entryTtl(randomTtl(SLOT_DAY_BASE_TTL, SLOT_DAY_TTL_JITTER_SECONDS));

        Map<String, RedisCacheConfiguration> perCache = Map.of(
                CacheNames.STORE_DETAIL, base.entryTtl(randomTtl(STORE_DETAIL_BASE_TTL, STORE_DETAIL_TTL_JITTER_SECONDS)),
                CacheNames.SLOT_DAY, base.entryTtl(randomTtl(SLOT_DAY_BASE_TTL, SLOT_DAY_TTL_JITTER_SECONDS))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    /**
     * JSON 序列化：Redis 里能直接看懂内容，方便排查。
     * 必须补 JavaTimeModule，否则 LocalDate/LocalTime 无法正确序列化；
     * GenericJackson2JsonRedisSerializer 默认带 @class 类型信息，反序列化能还原成原类型。
     */
    private static RedisSerializer<Object> jsonSerializer() {
        return new GenericJackson2JsonRedisSerializer()
                .configure(mapper -> {
                    mapper.registerModule(new JavaTimeModule());
                    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                });
    }

    /**
     * 随机 TTL：
     * - null（缓存空值防穿透）统一 60 秒，短 TTL 让"后来真的创建了数据"能尽快生效；
     * - 正常值 = 基础时长 + [0, jitter) 秒随机，打散过期时间，防雪崩。
     */
    private static RedisCacheWriter.TtlFunction randomTtl(Duration base, int jitterSeconds) {
        return (key, value) -> {
            if (value == null || value instanceof NullValue) {
                return NULL_VALUE_TTL;
            }
            return base.plusSeconds(ThreadLocalRandom.current().nextLong(jitterSeconds + 1L));
        };
    }
}

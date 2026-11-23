package com.example.booking.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户积分。只暴露"原子加积分"，不需要用户实体。
 * points = points + n 由数据库保证原子性，避免"先查后改"覆盖并发更新。
 */
public interface UserPointMapper {

    @Update("""
            UPDATE sys_user
               SET points = points + #{points},
                   updated_at = NOW()
             WHERE id = #{userId}
            """)
    int addPoints(@Param("userId") Long userId, @Param("points") int points);
}

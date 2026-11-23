package com.example.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.booking.domain.SysUser;
import com.example.booking.domain.SysUserAuth;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 管理端按手机号前缀筛选预约单时，先查出候选用户 ID。
     *
     * 为什么不把手机号直接拼进 SQL：任何用户输入拼 SQL 都有注入风险，
     * 先用参数化查询拿到 ID 列表，再在订单查询里用 IN，安全且能走 uk_phone 前缀索引。
     * LIMIT 200 防止极端前缀（如 "1"）拖回几十万行。
     */
    @Select("SELECT id FROM sys_user WHERE phone LIKE CONCAT(#{phonePrefix}, '%') AND deleted = 0 LIMIT 200")
    List<Long> selectIdsByPhonePrefix(@Param("phonePrefix") String phonePrefix);

    /** 登录专用：按手机号取密码哈希与角色，走 uk_phone 唯一索引 */
    @Select("""
            SELECT id, phone, nickname, password, role, store_id, status
            FROM sys_user
            WHERE phone = #{phone} AND deleted = 0
            """)
    SysUserAuth selectAuthByPhone(@Param("phone") String phone);
}

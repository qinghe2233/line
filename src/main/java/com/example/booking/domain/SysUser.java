package com.example.booking.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户实体（只映射管理端列表需要展示的列）。
 * 不映射 password / avatar 等敏感或非必要字段，查询时自然不会把它们带出来。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String phone;
    private String nickname;
    private Integer role;
    private Long storeId;
    private Integer status;
    private Integer points;

    @TableLogic
    private Integer deleted;
}

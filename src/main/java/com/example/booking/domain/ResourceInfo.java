package com.example.booking.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 资源实体（技师 / 房间 / 工位）。
 * 排班（时段库存）必须挂在一个资源上，前端新建时段时需要先从
 * GET /api/admin/resources 拿到可选资源列表。
 */
@Data
@TableName("resource_info")
public class ResourceInfo {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long storeId;
    private String name;
    private Integer type;
    private String description;
    private Integer status;

    private Integer deleted;
}

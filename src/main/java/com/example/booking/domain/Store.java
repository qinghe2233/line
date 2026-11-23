package com.example.booking.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalTime;

/**
 * 门店实体。只映射详情页需要的字段，避免实体膨胀。
 * deleted 字段配合全局逻辑删除配置，查询时自动追加 deleted = 0。
 */
@Data
@TableName("store")
public class Store {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;
    private String city;
    private String address;
    private String phone;
    private LocalTime businessStart;
    private LocalTime businessEnd;
    private Integer status;

    private Integer deleted;
}

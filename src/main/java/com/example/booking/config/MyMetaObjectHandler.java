package com.example.booking.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 公共字段自动填充。
 * 注意：实体字段必须标注 @TableField(fill = FieldFill.INSERT / INSERT_UPDATE) 才会生效，
 * strictXxxFill 只会处理带填充策略的字段，避免误改业务字段。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    private static final String CREATED_AT = "createdAt";
    private static final String UPDATED_AT = "updatedAt";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, CREATED_AT, LocalDateTime.class, now);
        strictInsertFill(metaObject, UPDATED_AT, LocalDateTime.class, now);
    }

    /**
     * 只刷新 updatedAt。
     * createdAt 一旦写入不允许被更新覆盖，这是审计字段的基本要求。
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, UPDATED_AT, LocalDateTime.class, LocalDateTime.now());
    }
}

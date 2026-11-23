package com.example.booking.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 通用分页返回体。
 * 只暴露前端需要的 4 个字段，不把 MyBatis-Plus 的 Page 对象直接返回，
 * 避免内部字段（如 optimizeCountSql、searchCount）泄漏到接口契约里。
 */
@Schema(description = "分页返回体")
public record PageResult<T>(

        @Schema(description = "当前页码")
        long page,

        @Schema(description = "每页条数")
        long size,

        @Schema(description = "总记录数")
        long total,

        @Schema(description = "总页数")
        long pages,

        @Schema(description = "当前页数据")
        List<T> records
) {

    public static <T> PageResult<T> of(long page, long size, long total, long pages, List<T> records) {
        return new PageResult<>(page, size, total, pages, records);
    }
}

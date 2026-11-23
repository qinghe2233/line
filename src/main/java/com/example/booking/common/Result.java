package com.example.booking.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import org.slf4j.MDC;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一返回体。
 * 约定：HTTP 状态码只表达"请求有没有被正确处理"，业务成败由 code 表达。
 * 因此业务异常仍返回 HTTP 200，前端只判断 code，避免把"预约满了"当成网络错误。
 */
@Getter
@Schema(description = "统一返回体")
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "业务码，0 表示成功", example = "0")
    private final int code;

    @Schema(description = "提示信息", example = "成功")
    private final String message;

    @Schema(description = "业务数据")
    private final T data;

    /** 与日志里的 traceId 一致，用户报障时可直接拿这个值检索全链路日志 */
    @Schema(description = "链路追踪 ID")
    private final String traceId;

    @Schema(description = "服务端时间戳（毫秒）")
    private final long timestamp;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = MDC.get(TraceIdFilter.TRACE_ID);
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}

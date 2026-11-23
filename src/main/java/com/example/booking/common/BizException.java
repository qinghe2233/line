package com.example.booking.common;

import lombok.Getter;

/**
 * 业务异常。
 * 继承 RuntimeException 而不是 Exception：业务校验失败属于正常分支，
 * 不需要调用方显式捕获，统一交给 GlobalExceptionHandler 转成 Result。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /** 需要覆盖默认提示时使用，例如带上具体的时段名 */
    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}

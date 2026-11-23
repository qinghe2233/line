package com.example.booking.common;

import lombok.Getter;

/**
 * 业务错误码。
 * 按模块分段，避免几百个错误码挤在一个区间里无法维护：
 * 0          成功
 * 10000-19999 通用/参数/请求
 * 20000-29999 用户与认证
 * 30000-39999 门店 / 服务项 / 资源
 * 40000-49999 预约 / 库存 / 支付
 * 50000-59999 排队 / 通知
 * 90000-99999 系统级异常
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "成功"),

    /* ---------------- 通用 10xxx ---------------- */
    PARAM_INVALID(10001, "参数校验失败"),
    UNAUTHORIZED(10002, "未登录或登录已过期"),
    FORBIDDEN(10003, "无权限访问"),
    NOT_FOUND(10004, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(10005, "请求方法不支持"),
    DUPLICATE_REQUEST(10006, "数据已存在，请勿重复提交"),

    /* ---------------- 用户与认证 20xxx ---------------- */
    USER_NOT_FOUND(20001, "用户不存在"),
    USER_PASSWORD_ERROR(20002, "手机号或密码错误"),
    USER_DISABLED(20003, "账号已被禁用"),
    PHONE_ALREADY_EXISTS(20004, "手机号已注册"),
    TOKEN_INVALID(20005, "Token 无效"),

    /* ---------------- 门店/服务/资源 30xxx ---------------- */
    STORE_NOT_FOUND(30001, "门店不存在"),
    STORE_CLOSED(30002, "门店已停业"),
    SERVICE_NOT_FOUND(30003, "服务项目不存在"),
    SERVICE_OFF_SHELF(30004, "服务项目已下架"),
    RESOURCE_NOT_FOUND(30005, "资源不存在"),
    RESOURCE_NOT_SUPPORT_SERVICE(30006, "该资源不提供此服务"),

    /* ---------------- 预约/库存/支付 40xxx ---------------- */
    SLOT_NOT_FOUND(40001, "时段不存在"),
    SLOT_SOLD_OUT(40002, "该时段已约满"),
    SLOT_CLOSED(40003, "该时段已停止预约"),
    ORDER_NOT_FOUND(40005, "预约单不存在"),
    ORDER_STATUS_ILLEGAL(40006, "当前订单状态不允许该操作"),
    ORDER_PAY_TIMEOUT(40007, "订单已超时关闭"),
    CANCEL_NOT_ALLOWED(40008, "当前时间不允许取消"),
    PAY_FAILED(40009, "支付失败"),
    REFUND_FAILED(40010, "退款失败"),
    PAY_SIGN_INVALID(40011, "支付回调验签失败"),
    PAY_AMOUNT_MISMATCH(40012, "支付金额与订单金额不一致"),

    /* ---------------- 排队/通知 50xxx ---------------- */
    TICKET_NOT_FOUND(50001, "排队号不存在"),
    TICKET_STATUS_ILLEGAL(50002, "当前排队状态不允许该操作"),

    /* ---------------- 系统 90xxx ---------------- */
    SYSTEM_ERROR(90001, "系统繁忙，请稍后重试"),
    DB_ERROR(90002, "数据库操作失败"),
    MQ_ERROR(90003, "消息发送失败"),
    TOO_MANY_REQUESTS(90004, "请求过于频繁，请稍后重试");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}

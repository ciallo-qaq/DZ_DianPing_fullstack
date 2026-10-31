package com.dzdp.dto;

/**
 * 统一业务状态码。HTTP 状态码仍用于传输、鉴权和限流语义。
 */
public enum StatusCode {
    SUCCESS(0, "成功"),
    BUSINESS_ERROR(40000, "请求失败"),
    INVALID_PARAM(40001, "参数错误"),
    KEYWORD_TOO_LONG(40002, "关键词不能超过10个字"),
    UNAUTHORIZED(40100, "未登录"),
    NOT_FOUND(40401, "数据不存在"),
    TOO_MANY_REQUESTS(42901, "请求过于频繁"),
    SYSTEM_ERROR(50000, "服务器异常"),
    NOT_IMPLEMENTED(50101, "该功能未实现");

    private final int code;
    private final String message;

    StatusCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /**
     * 兼容已有的 Result.fail("...") 调用，已知消息会映射到明确业务码。
     */
    public static StatusCode fromMessage(String message) {
        if ("该功能为实现".equals(message) || "功能未完成".equals(message)) {
            return NOT_IMPLEMENTED;
        }
        for (StatusCode statusCode : values()) {
            if (statusCode.message.equals(message)) {
                return statusCode;
            }
        }
        return BUSINESS_ERROR;
    }
}

package com.structengine.platform.common;

/**
 * 业务错误码定义，对齐 Go 版 httpx/errors.go；新增 503/504 覆盖游戏服 RPC 场景。
 */
public enum ErrorCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "输入不合法"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    TOO_LARGE(413, "文件过大或数量超限"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),
    UNPROCESSABLE(422, "数据解析失败"),
    UPSTREAM_ERROR(502, "上游推理失败"),
    GAME_SERVER_UNAVAILABLE(503, "游戏服务器未连接"),
    GAME_SERVER_TIMEOUT(504, "游戏服务器响应超时"),
    INTERNAL_ERROR(500, "存储失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
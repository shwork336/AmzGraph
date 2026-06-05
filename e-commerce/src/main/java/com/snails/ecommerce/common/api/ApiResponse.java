package com.snails.ecommerce.common.api;

/**
 * 统一 API 响应结构。
 *
 * <p>Controller 对外返回该结构，避免前端同时处理多种成功和失败响应格式。</p>
 *
 * @param success 请求是否成功
 * @param code 稳定错误码或 OK
 * @param message 面向调用方的错误或成功说明
 * @param data 成功响应的数据体
 */
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data
) {

    /**
     * 创建成功响应。
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", "OK", data);
    }

    /**
     * 创建失败响应。
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}

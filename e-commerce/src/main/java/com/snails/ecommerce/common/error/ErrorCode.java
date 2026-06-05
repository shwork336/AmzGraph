package com.snails.ecommerce.common.error;

/**
 * 对外暴露的稳定错误码。
 *
 * <p>前端和调用方应依赖错误码判断错误类型，而不是解析错误文案。</p>
 */
public enum ErrorCode {
    /** 请求参数缺失、格式错误或校验失败。 */
    INVALID_REQUEST,
    /** 上传文件为空、类型不支持、数量不合法或大小不合法。 */
    FILE_INVALID,
    /** 类目模板不存在或未启用。 */
    TEMPLATE_NOT_FOUND,
    /** Listing 任务不存在。 */
    TASK_NOT_FOUND,
    /** 当前任务状态不允许执行目标操作。 */
    TASK_STATUS_INVALID,
    /** 未预期的服务端错误。 */
    INTERNAL_ERROR
}

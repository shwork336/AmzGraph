package com.snails.ecommerce.common.error;

/**
 * 业务异常。
 *
 * <p>用于表达可预期的业务失败，例如模板不存在、任务不存在、任务状态不允许操作等。</p>
 */
public class BusinessException extends RuntimeException {

    /** 与异常对应的稳定错误码。 */
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

package com.snails.ecommerce.common.error;

import com.snails.ecommerce.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
/**
 * 全局异常处理器。
 *
 * <p>把业务异常、参数异常、文件上传异常和未知异常统一转换为 {@code ApiResponse}。</p>
 */
public class GlobalExceptionHandler {

    /**
     * 业务异常固定返回 400，由错误码表达具体业务原因。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(exception.getErrorCode().name(), exception.getMessage()));
    }

    /**
     * Spring MVC 参数绑定和校验失败统一归类为请求无效。
     */
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(Exception exception) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_REQUEST.name(), exception.getMessage()));
    }

    /**
     * 文件超过上传限制时返回文件错误码。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(ErrorCode.FILE_INVALID.name(), "Uploaded file is too large"));
    }

    /**
     * 兜底未知异常，避免把服务端内部细节暴露给前端。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.name(), "Internal server error"));
    }
}

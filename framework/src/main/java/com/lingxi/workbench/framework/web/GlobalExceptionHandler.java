package com.lingxi.workbench.framework.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.ApiResponse;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 所有启动模块共用的全局异常处理器。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity.status(exception.getHttpStatus().value())
                .body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException() {
        return failure(HttpStatusEnum.BAD_REQUEST, "请求参数不合法");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage() {
        return failure(HttpStatusEnum.BAD_REQUEST, "请求体格式不正确");
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotLoginException() {
        return failure(HttpStatusEnum.UNAUTHORIZED, "登录状态无效，请重新登录");
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public ResponseEntity<ApiResponse<Void>> handleForbiddenException() {
        return failure(HttpStatusEnum.FORBIDDEN, "无权访问该资源");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        LOGGER.error("未处理的系统异常", exception);
        return failure(HttpStatusEnum.INTERNAL_SERVER_ERROR, "系统内部错误");
    }

    private static ResponseEntity<ApiResponse<Void>> failure(HttpStatusEnum status, String message) {
        return ResponseEntity.status(status.value())
                .body(ApiResponse.failure(status, message));
    }
}

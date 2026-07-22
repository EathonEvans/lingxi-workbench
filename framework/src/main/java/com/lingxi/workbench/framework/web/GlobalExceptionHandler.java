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
 *
 * <p>按异常类型统一转换为标准的 {@link ApiResponse} 错误响应，
 * 避免异常细节泄漏到接口响应中。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 处理业务异常，返回异常中携带的自定义错误码和提示信息。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity.status(exception.getHttpStatus().getCode())
                .body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
    }

    /** 处理 JSR-303 参数校验失败，返回 400 通用提示。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException() {
        return failure(HttpStatusEnum.BAD_REQUEST, "请求参数不合法");
    }

    /** 处理请求体 JSON 解析或类型转换失败，返回 400 通用提示。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage() {
        return failure(HttpStatusEnum.BAD_REQUEST, "请求体格式不正确");
    }

    /** 处理登录态失效或未登录，返回 401 通用提示。 */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotLoginException() {
        return failure(HttpStatusEnum.UNAUTHORIZED, "登录状态无效，请重新登录");
    }

    /** 处理权限不足和角色不匹配，返回 403 通用提示。 */
    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public ResponseEntity<ApiResponse<Void>> handleForbiddenException() {
        return failure(HttpStatusEnum.FORBIDDEN, "无权访问该资源");
    }

    /**
     * 兜底处理所有未被明确捕获的异常，记录完整堆栈并返回 500 通用提示。
     *
     * <p>不向客户端返回异常详情，防止内部实现泄漏。</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        LOGGER.error("未处理的系统异常", exception);
        return failure(HttpStatusEnum.INTERNAL_SERVER_ERROR, "系统内部错误");
    }

    /** 构建统一失败响应体，包含 HTTP 状态码和错误信息。 */
    private static ResponseEntity<ApiResponse<Void>> failure(HttpStatusEnum status, String message) {
        return ResponseEntity.status(status.getCode())
                .body(ApiResponse.failure(status, message));
    }
}

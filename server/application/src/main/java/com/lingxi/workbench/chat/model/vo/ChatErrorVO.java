package com.lingxi.workbench.chat.model.vo;

/**
 * SSE 对话错误数据。
 *
 * @param code    错误码
 * @param message 用户可见的错误说明
 */
public record ChatErrorVO(String code, String message) {
}

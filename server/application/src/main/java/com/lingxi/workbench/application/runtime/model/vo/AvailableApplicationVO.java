package com.lingxi.workbench.application.runtime.model.vo;

/**
 * 应用端可选择的已发布 AI 应用。
 *
 * @param code        应用稳定编码，对话请求使用该编码解析当前生效版本
 * @param name        应用名称
 * @param description 应用说明，可为空
 * @param assistant   当前生效版本冻结的助手身份
 */
public record AvailableApplicationVO(
        String code,
        String name,
        String description,
        ApplicationAssistantIdentityVO assistant
) {
}

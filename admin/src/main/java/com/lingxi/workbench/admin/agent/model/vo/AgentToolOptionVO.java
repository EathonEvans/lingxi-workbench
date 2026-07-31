package com.lingxi.workbench.admin.agent.model.vo;

/**
 * 管理端可选择的本地工具。
 *
 * @param code        稳定工具编码
 * @param displayName 展示名称
 * @param description 使用说明
 */
public record AgentToolOptionVO(
        String code,
        String displayName,
        String description) {
}

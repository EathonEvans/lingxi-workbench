package com.lingxi.workbench.admin.knowledge.model.vo;

/**
 * 授权选择器仅暴露主体标识和名称，不暴露账号资料或系统管理权限。
 * @param id 主体标识
 * @param label 显示名称
 */
public record KnowledgeAccessOptionVO(Long id, String label) {
}

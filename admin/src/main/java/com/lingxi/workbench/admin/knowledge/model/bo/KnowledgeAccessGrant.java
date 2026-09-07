package com.lingxi.workbench.admin.knowledge.model.bo;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeAccessSubjectType;

/**
 * 一条知识库读取授权。
 * @param subjectType 主体类型，USER、ROLE 或 DEPARTMENT
 * @param subjectId 主体主键
 */
public record KnowledgeAccessGrant(KnowledgeAccessSubjectType subjectType, Long subjectId) {
}

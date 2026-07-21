package com.lingxi.workbench.admin.knowledge.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import java.time.OffsetDateTime;

/** 知识库列表响应。 */
public record KnowledgeBaseVO(String id, String name, String code, String description,
                              EnableStatus status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

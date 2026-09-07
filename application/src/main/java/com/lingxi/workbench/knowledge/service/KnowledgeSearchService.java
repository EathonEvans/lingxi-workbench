package com.lingxi.workbench.knowledge.service;

import com.lingxi.workbench.knowledge.model.bo.KnowledgeSearchResponse;

import com.lingxi.workbench.knowledge.model.bo.KnowledgeSearchRequest;

/** 用户范围内的版本化知识检索，供对话 Tool 使用。 */
public interface KnowledgeSearchService {
    /** 先鉴权再召回和可选重排；权限或外部调用失败均不能伪装成空召回。 */
    KnowledgeSearchResponse searchVersion(KnowledgeSearchRequest request);
}

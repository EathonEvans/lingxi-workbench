package com.lingxi.workbench.admin.agent.model.vo;

import com.lingxi.workbench.admin.agent.common.enums.KnowledgeBindingMode;

/** Agent 知识库绑定响应。
 *  @param knowledgeBaseId          知识库ID
 *  @param embeddingModelInstanceId Embedding模型实例ID
 *  @param bindingMode              数据版本绑定模式
 *  @param priority                 绑定优先级
 */
public record AgentKnowledgeBindingVO(
        String knowledgeBaseId,
        String embeddingModelInstanceId,
        KnowledgeBindingMode bindingMode,
        Integer priority) {
}

package com.lingxi.workbench.chat.service.node;

import com.lingxi.workbench.chat.common.enums.ChatProcessResult;
import com.lingxi.workbench.chat.common.enums.RetrievalStatus;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.service.AbstractChatProcessNode;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.knowledge.model.vo.KnowledgeSearchResultVO;
import com.lingxi.workbench.knowledge.service.KnowledgeSearchService;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import java.util.List;

/**
 * 使用知识库绑定的 Embedding 模型执行向量召回。
 */
@Slf4j
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class KnowledgeRetrievalNode extends AbstractChatProcessNode {

    private final KnowledgeSearchService knowledgeSearchService;
    private final ModelRuntimeConfigService modelRuntimeConfigService;

    /**
     * 使用知识库绑定的 Embedding 模型执行向量检索。
     * 未绑定知识库时跳过检索；知识库不可用或检索失败时，根据 AgentVersion 的失败策略处理。
     */
    @Override
    protected ChatProcessResult processCurrent(ModelChatContext context) {
        Long knowledgeBaseId = context.getKnowledgeBaseId();
        if (knowledgeBaseId == null) {
            context.setKnowledgeChunks(List.of());
            context.setRetrievalStatus(RetrievalStatus.SKIPPED);
            return ChatProcessResult.CONTINUE;
        }

        try {
            /*
             * 这里不再读取知识库“当前”的模型，也不再使用 application.yml 默认召回参数。
             * 这些值已经冻结在 AgentVersion 中，运行时必须完整复现发布时的检索契约。
             */
            EmbeddingModelRuntimeConfig embeddingConfig = modelRuntimeConfigService.getEmbeddingModelConfig(context.getEmbeddingModelInstanceId());

            List<KnowledgeSearchResultVO> chunks = knowledgeSearchService.search(
                    context.retrievalQuery(),
                    knowledgeBaseId,
                    embeddingConfig,
                    context.getRetrievalTopK(),
                    context.getRetrievalSimilarityThreshold());

            context.setKnowledgeChunks(chunks);
            RetrievalStatus retrievalStatus = RetrievalStatus.SUCCESS;
            if (chunks.isEmpty()) {
                retrievalStatus = RetrievalStatus.EMPTY;
            }
            context.setRetrievalStatus(retrievalStatus);
        } catch (RuntimeException exception) {
            /*
             * FAIL_REQUEST 是显式的业务选择，必须把异常继续交给上层推进任务失败状态。
             * 其他策略才允许降级为无知识上下文回答，并记录可操作日志。
             */
            if ("FAIL_REQUEST".equals(context.getRetrievalFailureStrategy())) {
                throw exception;
            }
            log.warn("知识库检索失败，降级为普通对话。knowledgeBaseId={}", knowledgeBaseId, exception);
            context.setKnowledgeChunks(List.of());
            context.setRetrievalStatus(RetrievalStatus.DEGRADED);
        }
        return ChatProcessResult.CONTINUE;
    }
}

package com.lingxi.workbench.application.runtime.service.impl.strategy;

import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.service.PromptRuntimeService;
import com.lingxi.workbench.chat.model.bo.QueryRewriteConfig;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.service.ChatProcessChain;
import com.lingxi.workbench.chat.service.impl.ModelRequestFactory;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import static com.lingxi.workbench.application.runtime.common.constants.AgentChatConstants.RAG_CHAT_AGENT_TYPE;

/** RAG_CHAT 专属的历史、问题改写、向量检索与 Prompt 处理策略。 */
@Component
public class RagChatPreparationStrategy extends AbstractAgentChatPreparationStrategy {

    private final ModelRuntimeConfigService modelRuntimeConfigService;
    private final PromptRuntimeService promptRuntimeService;
    private final ModelRequestFactory modelRequestFactory;

    public RagChatPreparationStrategy(PromptRuntimeService promptRuntimeService, ModelRuntimeConfigService modelRuntimeConfigService, ModelRequestFactory modelRequestFactory, @Qualifier("ragChatProcessChain") ChatProcessChain processChain) {
        super(promptRuntimeService, processChain);
        this.promptRuntimeService = promptRuntimeService;
        this.modelRuntimeConfigService = modelRuntimeConfigService;
        this.modelRequestFactory = modelRequestFactory;
    }

    @Override
    public String agentType() {
        return RAG_CHAT_AGENT_TYPE;
    }

    @Override
    public ModelChatContext prepare(AgentRuntimeDefinition definition, ChatExecutionContext execution) {
        validate(definition);
        AgentRuntimeDefinition.KnowledgeBinding knowledge = definition.getKnowledge();
        AgentRuntimeDefinition.RetrievalPolicy retrieval = definition.getRetrievalPolicy();
        ModelChatContext context = commonContext(definition, execution)
                .knowledgeBaseId(knowledge.knowledgeBaseId())
                .embeddingModelInstanceId(knowledge.embeddingModelInstanceId())
                .queryRewriteConfig(resolveQueryRewriteConfig(definition))
                .retrievalTopK(retrieval.getTopK())
                .retrievalSimilarityThreshold(retrieval.getSimilarityThreshold().doubleValue())
                .retrievalFailureStrategy(retrieval.getFailureStrategy())
                .build();
        ModelChatContext preparedContext = process(context);
        preparedContext.setPromptPair(modelRequestFactory.createRetrievalPrompt(preparedContext));
        return preparedContext;
    }

    private QueryRewriteConfig resolveQueryRewriteConfig(AgentRuntimeDefinition definition) {
        AgentRuntimeDefinition.RewritePolicy policy = definition.getRewritePolicy();
        if (Objects.isNull(policy) || !Boolean.TRUE.equals(policy.getEnabled())) {
            return QueryRewriteConfig.disabled();
        }
        ChatModelRuntimeConfig modelConfig = applyRewritePolicy(modelRuntimeConfigService.getChatModelConfig(definition.getRewriteModelInstanceId()), policy);
        return QueryRewriteConfig.builder().enabled(true).modelConfig(modelConfig).prompt(promptRuntimeService.resolvePublished(definition.getRewritePromptVersionId())).maxHistoryRounds(policy.getMaxHistoryRounds()).maxHistoryTokens(policy.getMaxHistoryTokens()).maxOutputCharacters(policy.getMaxOutputCharacters()).timeoutMillis(policy.getTimeoutMillis()).fallbackToOriginal(Boolean.TRUE.equals(policy.getFallbackToOriginal())).build();
    }

    private static ChatModelRuntimeConfig applyRewritePolicy(ChatModelRuntimeConfig source, AgentRuntimeDefinition.RewritePolicy policy) {
        Double temperature = Optional.ofNullable(policy.getTemperature())
                .map(BigDecimal::doubleValue)
                .orElse(source.getTemperature());
        int timeoutSeconds = Math.max((policy.getTimeoutMillis() + 999) / 1000, 1);
        return ChatModelRuntimeConfig.builder().instanceId(source.getInstanceId()).baseUrl(source.getBaseUrl()).apiKey(source.getApiKey()).modelCode(source.getModelCode()).temperature(temperature).topP(source.getTopP()).maxTokens(policy.getMaxOutputTokens()).stopSequences(source.getStopSequences()).extensionParameters(source.getExtensionParameters()).timeoutSeconds(timeoutSeconds).maxRetries(source.getMaxRetries()).build();
    }

    private static void validate(AgentRuntimeDefinition definition) {
        AgentRuntimeDefinition.KnowledgeBinding knowledge = definition.getKnowledge();
        if (Objects.isNull(knowledge) || Objects.isNull(knowledge.knowledgeBaseId()) || Objects.isNull(knowledge.embeddingModelInstanceId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion知识库配置不完整");
        }
        AgentRuntimeDefinition.RetrievalPolicy retrieval = definition.getRetrievalPolicy();
        if (Objects.isNull(retrieval) || Objects.isNull(retrieval.getTopK()) || Objects.isNull(retrieval.getSimilarityThreshold()) || Objects.isNull(retrieval.getFailureStrategy())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion检索策略不完整");
        }
        validateRewritePolicy(definition);
    }

    private static void validateRewritePolicy(AgentRuntimeDefinition definition) {
        AgentRuntimeDefinition.RewritePolicy rewrite = definition.getRewritePolicy();
        if (Objects.isNull(rewrite) || !Boolean.TRUE.equals(rewrite.getEnabled())) {
            return;
        }
        if (Objects.isNull(definition.getRewriteModelInstanceId()) || Objects.isNull(definition.getRewritePromptVersionId()) || Objects.isNull(rewrite.getMaxHistoryRounds()) || Objects.isNull(rewrite.getMaxHistoryTokens()) || Objects.isNull(rewrite.getTemperature()) || Objects.isNull(rewrite.getMaxOutputTokens()) || Objects.isNull(rewrite.getMaxOutputCharacters()) || Objects.isNull(rewrite.getTimeoutMillis()) || Objects.isNull(rewrite.getFallbackToOriginal())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion问题改写配置不完整");
        }
    }
}

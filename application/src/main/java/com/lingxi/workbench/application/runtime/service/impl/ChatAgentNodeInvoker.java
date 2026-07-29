package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ChatWorkflowExecutionContext;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.AgentRuntimeService;
import com.lingxi.workbench.application.runtime.service.ChatWorkflowExecutionRegistry;
import com.lingxi.workbench.application.runtime.service.PromptRuntimeService;
import com.lingxi.workbench.chat.common.enums.RetrievalStatus;
import com.lingxi.workbench.chat.model.bo.PromptPair;
import com.lingxi.workbench.chat.model.bo.QueryRewriteConfig;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.service.ChatProcessChain;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.StreamingChatGateway;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import com.lingxi.workbench.workflow.model.AgentNodeInvocation;
import com.lingxi.workbench.workflow.service.AgentNodeInvoker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * 将 Workflow 的 Agent 节点适配到现有 Chat/RAG 处理链。
 */
@Component
@RequiredArgsConstructor
public class ChatAgentNodeInvoker implements AgentNodeInvoker {

    private static final String EMPTY_KNOWLEDGE_HINT = "未检索到足够相关的知识库资料。";

    private final AgentRuntimeService agentRuntimeService;
    private final PromptRuntimeService promptRuntimeService;
    private final ChatWorkflowExecutionRegistry executionRegistry;
    private final ModelRuntimeConfigService modelRuntimeConfigService;
    private final ChatProcessChain chatProcessChain;
    private final StreamingChatGateway streamingChatGateway;

    /**
     * 将 Workflow Engine 的 Agent 节点调用适配到现有 Chat/RAG 处理链。
     * 校验 AgentVersion 类型支持、加载配置、执行处理链、构建非流式 Prompt 并生成完整回答。
     *
     * @param invocation Agent 节点调用上下文
     * @return 包含 answer 字段的输出 Map
     */
    @Override
    public Map<String, Object> invoke(AgentNodeInvocation invocation) {
        AgentRuntimeSnapshot snapshot = agentRuntimeService.resolveVersion(
                invocation.getAgentId(),
                invocation.getAgentVersionId());
        AgentRuntimeDefinition definition = snapshot.definition();
        validateSupported(definition);

        Object queryValue = invocation.getInputs().get("query");
        if (!(queryValue instanceof String query) || query.isBlank()) {
            throw new BusinessException(
                    HttpStatusEnum.BAD_REQUEST,
                    "Agent节点输入query必须是非空字符串: " + invocation.getNodeId());
        }

        ChatWorkflowExecutionContext execution = executionRegistry.require(invocation.getExecutionId());
        String originalQuery = execution.runtimeVariables().require(
                RuntimeVariableAccessors.USER_QUERY);
        if (!originalQuery.equals(query)) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT,
                    "当前阶段仅支持入口Agent使用用户原始query");
        }

        ModelChatContext context = createChatContext(definition, execution, invocation);
        chatProcessChain.process(context);

        if (context.getRetrievalStatus() == RetrievalStatus.EMPTY
                && definition.getRetrievalPolicy() != null
                && "RETURN_EMPTY_HINT".equals(
                definition.getRetrievalPolicy().getEmptyResultStrategy())) {
            return Map.of("answer", EMPTY_KNOWLEDGE_HINT);
        }

        PromptPair prompt = context.getPromptPair();
        ChatModelRuntimeConfig modelConfig = applyGenerationPolicy(
                modelRuntimeConfigService.getChatModelConfig(
                        definition.getChatModelInstanceId()),
                definition.getGenerationPolicy());
        String answer = generate(execution, prompt, context, modelConfig, timeout(definition));
        return Map.of("answer", answer);
    }

    private ModelChatContext createChatContext(
            AgentRuntimeDefinition definition,
            ChatWorkflowExecutionContext execution,
            AgentNodeInvocation invocation) {
        /*
         * 知识库、Embedding 模型和召回参数必须来自已发布的 AgentVersion 快照。
         * ChatRequest 不提供这些字段，避免调用方越权覆盖配置，也保证同一版本可重复执行。
         */
        AgentRuntimeDefinition.KnowledgeBinding knowledgeBinding = definition.getKnowledge();
        AgentRuntimeDefinition.MemoryPolicy memory = definition.getMemoryPolicy();
        AgentRuntimeDefinition.RetrievalPolicy retrieval = definition.getRetrievalPolicy();

        Boolean memoryEnabled = null;
        Integer memoryMaxRounds = null;
        Integer memoryMaxTokens = null;
        if (memory != null) {
            memoryEnabled = memory.enabled();
            memoryMaxRounds = memory.maxRounds();
            memoryMaxTokens = memory.maxTokens();
        }

        return ModelChatContext.builder()
                .runtimeVariables(execution.runtimeVariables())
                .clientMessageId(invocation.getExecutionId() + ":" + invocation.getNodeId())
                .knowledgeBaseId(knowledgeBinding.knowledgeBaseId())
                .embeddingModelInstanceId(knowledgeBinding.embeddingModelInstanceId())
                .memoryEnabled(memoryEnabled)
                .memoryMaxRounds(memoryMaxRounds)
                .memoryMaxTokens(memoryMaxTokens)
                .queryRewriteConfig(resolveQueryRewriteConfig(definition))
                .retrievalTopK(retrieval.getTopK())
                .retrievalSimilarityThreshold(retrieval.getSimilarityThreshold().doubleValue())
                .retrievalFailureStrategy(retrieval.getFailureStrategy())
                .systemPrompt(promptRuntimeService.resolvePublished(
                        definition.resolveSystemPromptVersionId()))
                .userPrompt(resolveUserPrompt(definition))
                .turn(execution.turn())
                .modelUsageRecorder(execution::recordUsage)
                .build();
    }

    private QueryRewriteConfig resolveQueryRewriteConfig(
            AgentRuntimeDefinition definition) {
        AgentRuntimeDefinition.RewritePolicy policy =
                definition.getRewritePolicy();
        if (policy == null || !Boolean.TRUE.equals(policy.getEnabled())) {
            return QueryRewriteConfig.disabled();
        }
        ChatModelRuntimeConfig modelConfig = applyRewritePolicy(
                modelRuntimeConfigService.getChatModelConfig(
                        definition.getRewriteModelInstanceId()),
                policy);
        return QueryRewriteConfig.builder()
                .enabled(true)
                .modelConfig(modelConfig)
                .prompt(promptRuntimeService.resolvePublished(
                        definition.getRewritePromptVersionId()))
                .maxHistoryRounds(policy.getMaxHistoryRounds())
                .maxHistoryTokens(policy.getMaxHistoryTokens())
                .maxOutputCharacters(policy.getMaxOutputCharacters())
                .timeoutMillis(policy.getTimeoutMillis())
                .fallbackToOriginal(Boolean.TRUE.equals(
                        policy.getFallbackToOriginal()))
                .build();
    }

    private PromptRuntimeSnapshot resolveUserPrompt(
            AgentRuntimeDefinition definition) {
        if (definition.getUserPromptVersionId() == null) {
            return null;
        }
        return promptRuntimeService.resolvePublished(
                definition.getUserPromptVersionId());
    }

    private String generate(
            ChatWorkflowExecutionContext execution,
            PromptPair prompt,
            ModelChatContext context,
            ChatModelRuntimeConfig modelConfig,
            Duration timeout) {
        StringBuilder answer = new StringBuilder();
        ChatStreamCommand command = new ChatStreamCommand(
                prompt.userPrompt(),
                prompt.systemPrompt(),
                context.getRuntimeVariables().require(
                        RuntimeVariableAccessors.SESSION_HISTORY),
                modelConfig);
        streamingChatGateway.stream(command)
                .doOnNext(chunk -> {
                    answer.append(chunk.content());
                    execution.recordUsage(chunk);
                })
                .then()
                .block(timeout);
        return answer.toString();
    }

    private static ChatModelRuntimeConfig applyGenerationPolicy(
            ChatModelRuntimeConfig source,
            AgentRuntimeDefinition.GenerationPolicy policy) {
        if (policy == null) {
            return source;
        }
        Double temperature = source.getTemperature();
        if (policy.getTemperature() != null) {
            temperature = policy.getTemperature().doubleValue();
        }
        Double topP = source.getTopP();
        if (policy.getTopP() != null) {
            topP = policy.getTopP().doubleValue();
        }
        Integer maxTokens = Objects.requireNonNullElse(
                policy.getMaxOutputTokens(),
                source.getMaxTokens());
        Integer timeoutSeconds = Objects.requireNonNullElse(
                policy.getTimeoutSeconds(),
                source.getTimeoutSeconds());
        Integer maxRetries = Objects.requireNonNullElse(
                policy.getMaxRetries(),
                source.getMaxRetries());

        return ChatModelRuntimeConfig.builder()
                .instanceId(source.getInstanceId())
                .baseUrl(source.getBaseUrl())
                .apiKey(source.getApiKey())
                .modelCode(source.getModelCode())
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxTokens)
                .stopSequences(source.getStopSequences())
                .extensionParameters(source.getExtensionParameters())
                .timeoutSeconds(timeoutSeconds)
                .maxRetries(maxRetries)
                .build();
    }

    private static ChatModelRuntimeConfig applyRewritePolicy(ChatModelRuntimeConfig source, AgentRuntimeDefinition.RewritePolicy policy) {
        Double temperature = source.getTemperature();
        if (policy.getTemperature() != null) {
            temperature = policy.getTemperature().doubleValue();
        }
        int timeoutSeconds = Math.max(
                (policy.getTimeoutMillis() + 999) / 1000,
                1);
        return ChatModelRuntimeConfig.builder()
                .instanceId(source.getInstanceId())
                .baseUrl(source.getBaseUrl())
                .apiKey(source.getApiKey())
                .modelCode(source.getModelCode())
                .temperature(temperature)
                .topP(source.getTopP())
                .maxTokens(policy.getMaxOutputTokens())
                .stopSequences(source.getStopSequences())
                .extensionParameters(source.getExtensionParameters())
                .timeoutSeconds(timeoutSeconds)
                .maxRetries(source.getMaxRetries())
                .build();
    }

    private static Duration timeout(AgentRuntimeDefinition definition) {
        Integer totalTimeout = null;
        if (definition.getRuntimePolicy() != null) {
            totalTimeout = definition.getRuntimePolicy().totalTimeoutSeconds();
        }
        Integer generationTimeout = null;
        if (definition.getGenerationPolicy() != null) {
            generationTimeout = definition.getGenerationPolicy().getTimeoutSeconds();
        }
        int seconds = Math.min(
                Objects.requireNonNullElse(totalTimeout, 90),
                Objects.requireNonNullElse(generationTimeout, 60));
        return Duration.ofSeconds(Math.max(seconds, 1));
    }

    private static void validateSupported(AgentRuntimeDefinition definition) {
        if (!"RAG_CHAT".equals(definition.getAgentType())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "当前运行时仅支持RAG_CHAT Agent");
        }
        if (definition.getChatModelInstanceId() == null
                || definition.resolveSystemPromptVersionId() == null) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT,
                    "AgentVersion缺少模型或SYSTEM Prompt");
        }
        if (definition.getSchemaVersion() != null
                && definition.getSchemaVersion() >= 2
                && definition.getUserPromptVersionId() == null) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT,
                    "AgentVersion缺少USER Prompt");
        }
        AgentRuntimeDefinition.KnowledgeBinding knowledgeBinding = definition.getKnowledge();
        if (knowledgeBinding == null
                || knowledgeBinding.knowledgeBaseId() == null
                || knowledgeBinding.embeddingModelInstanceId() == null) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion知识库配置不完整");
        }
        AgentRuntimeDefinition.RetrievalPolicy retrievalPolicy = definition.getRetrievalPolicy();
        if (retrievalPolicy == null
                || retrievalPolicy.getTopK() == null
                || retrievalPolicy.getSimilarityThreshold() == null
                || retrievalPolicy.getFailureStrategy() == null) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion检索策略不完整");
        }
        AgentRuntimeDefinition.RewritePolicy rewritePolicy =
                definition.getRewritePolicy();
        if (rewritePolicy != null
                && Boolean.TRUE.equals(rewritePolicy.getEnabled())
                && (definition.getRewriteModelInstanceId() == null
                || definition.getRewritePromptVersionId() == null
                || rewritePolicy.getMaxHistoryRounds() == null
                || rewritePolicy.getMaxHistoryTokens() == null
                || rewritePolicy.getTemperature() == null
                || rewritePolicy.getMaxOutputTokens() == null
                || rewritePolicy.getMaxOutputCharacters() == null
                || rewritePolicy.getTimeoutMillis() == null
                || rewritePolicy.getFallbackToOriginal() == null)) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT,
                    "AgentVersion问题改写配置不完整");
        }
    }
}

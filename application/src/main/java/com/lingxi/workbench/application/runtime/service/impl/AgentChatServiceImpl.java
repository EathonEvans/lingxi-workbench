package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.AgentChatService;
import com.lingxi.workbench.application.runtime.service.AgentRuntimeService;
import com.lingxi.workbench.application.runtime.service.PromptRuntimeService;
import com.lingxi.workbench.application.runtime.service.ToolRuntimeService;
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
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import static com.lingxi.workbench.infra.ai.tool.common.constants.AgentToolLimitConstants.MAX_RESULT_CHARACTERS;
import static com.lingxi.workbench.infra.ai.tool.common.constants.AgentToolLimitConstants.MAX_TOOL_CALLS;
import static com.lingxi.workbench.infra.ai.tool.common.constants.AgentToolLimitConstants.MIN_RESULT_CHARACTERS;
import static com.lingxi.workbench.infra.ai.tool.common.constants.AgentToolLimitConstants.MIN_TOOL_CALLS;

import static com.lingxi.workbench.application.runtime.common.constants.AgentChatConstants.EMPTY_KNOWLEDGE_HINT;
import static com.lingxi.workbench.application.runtime.common.constants.AgentChatConstants.RETURN_EMPTY_HINT_STRATEGY;
import static com.lingxi.workbench.application.runtime.common.constants.AgentChatConstants.TOOL_AUGMENTED_SYSTEM_PROMPT;

/**
 * Agent 对话主流程。
 *
 * <p>这是阅读 Agent 运行时的主入口，执行顺序固定为：</p>
 *
 * <ol>
 *     <li>加载并校验发布后的 AgentVersion；</li>
 *     <li>根据版本配置构建前置处理上下文；</li>
 *     <li>执行记忆、问题改写、知识检索和 Prompt 构建链；</li>
 *     <li>装配回答模型与工具配置；</li>
 *     <li>调用模型并汇总最终回答和 Token 用量。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class AgentChatServiceImpl implements AgentChatService {

    private final AgentRuntimeService agentRuntimeService;
    private final PromptRuntimeService promptRuntimeService;
    private final ModelRuntimeConfigService modelRuntimeConfigService;
    private final ChatProcessChain chatProcessChain;
    private final StreamingChatGateway streamingChatGateway;
    private final ToolRuntimeService toolRuntimeService;

    @Override
    public String chat(Long agentId, Long agentVersionId, String query, ChatExecutionContext execution) {
        AgentRuntimeSnapshot snapshot = agentRuntimeService.resolveVersion(agentId, agentVersionId);
        AgentRuntimeDefinition definition = snapshot.definition();
        validateSupported(definition);
        validateEntryQuery(query, execution);

        ModelChatContext chatContext = createChatContext(definition, execution);
        chatProcessChain.process(chatContext);

        if (shouldReturnEmptyKnowledgeHint(chatContext, definition)) {
            execution.getEventPublisher().publishText(new ChatStreamChunk(EMPTY_KNOWLEDGE_HINT));
            return EMPTY_KNOWLEDGE_HINT;
        }

        PromptPair prompt = chatContext.getPromptPair();
        ChatModelRuntimeConfig modelConfig = applyGenerationPolicy(modelRuntimeConfigService.getChatModelConfig(definition.getChatModelInstanceId()), definition.getGenerationPolicy());
        return this.generate(execution, definition, prompt, chatContext, modelConfig, timeout(definition));
    }

    /**
     * 当前阶段只允许入口 Agent 处理用户原始问题。
     *
     * <p>后续支持多 Agent 路由时，应由上层显式构造新的节点输入语义，而不是静默覆盖
     * RuntimeVariables 中的用户原始问题。</p>
     */
    private static void validateEntryQuery(String query, ChatExecutionContext execution) {
        String originalQuery = execution.getRuntimeVariables()
                .require(RuntimeVariableAccessors.USER_QUERY);
        if (!originalQuery.equals(query)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "当前阶段仅支持入口Agent使用用户原始query");
        }
    }

    private ModelChatContext createChatContext(AgentRuntimeDefinition definition, ChatExecutionContext execution) {
        /*
         * 知识库、Embedding 模型和召回参数必须来自已发布的 AgentVersion 快照。
         * 对话请求不能覆盖这些字段，确保同一版本能够被稳定复现。
         */
        AgentRuntimeDefinition.KnowledgeBinding knowledgeBinding = definition.getKnowledge();
        AgentRuntimeDefinition.MemoryPolicy memory = definition.getMemoryPolicy();
        AgentRuntimeDefinition.RetrievalPolicy retrieval = definition.getRetrievalPolicy();

        Boolean memoryEnabled = null;
        Integer memoryMaxRounds = null;
        Integer memoryMaxTokens = null;
        if (Objects.nonNull(memory)) {
            memoryEnabled = memory.enabled();
            memoryMaxRounds = memory.maxRounds();
            memoryMaxTokens = memory.maxTokens();
        }

        return ModelChatContext.builder()
                .runtimeVariables(execution.getRuntimeVariables())
                .knowledgeBaseId(knowledgeBinding.knowledgeBaseId())
                .embeddingModelInstanceId(knowledgeBinding.embeddingModelInstanceId())
                .memoryEnabled(memoryEnabled)
                .memoryMaxRounds(memoryMaxRounds)
                .memoryMaxTokens(memoryMaxTokens)
                .queryRewriteConfig(resolveQueryRewriteConfig(definition))
                .retrievalTopK(retrieval.getTopK())
                .retrievalSimilarityThreshold(retrieval.getSimilarityThreshold().doubleValue())
                .retrievalFailureStrategy(retrieval.getFailureStrategy())
                .systemPrompt(promptRuntimeService.resolvePublished(definition.resolveSystemPromptVersionId()))
                .userPrompt(resolveUserPrompt(definition).orElse(null))
                .turn(execution.getTurn())
                .modelUsageRecorder(execution::recordUsage)
                .build();
    }

    private QueryRewriteConfig resolveQueryRewriteConfig(AgentRuntimeDefinition definition) {
        AgentRuntimeDefinition.RewritePolicy policy = definition.getRewritePolicy();
        if (Objects.isNull(policy) || !Boolean.TRUE.equals(policy.getEnabled())) {
            return QueryRewriteConfig.disabled();
        }
        ChatModelRuntimeConfig modelConfig = applyRewritePolicy(modelRuntimeConfigService.getChatModelConfig(definition.getRewriteModelInstanceId()), policy);
        return QueryRewriteConfig.builder().enabled(true).modelConfig(modelConfig).prompt(promptRuntimeService.resolvePublished(definition.getRewritePromptVersionId())).maxHistoryRounds(policy.getMaxHistoryRounds()).maxHistoryTokens(policy.getMaxHistoryTokens()).maxOutputCharacters(policy.getMaxOutputCharacters()).timeoutMillis(policy.getTimeoutMillis()).fallbackToOriginal(Boolean.TRUE.equals(policy.getFallbackToOriginal())).build();
    }

    private Optional<PromptRuntimeSnapshot> resolveUserPrompt(AgentRuntimeDefinition definition) {
        if (Objects.isNull(definition.getUserPromptVersionId())) {
            return Optional.empty();
        }
        return Optional.of(promptRuntimeService.resolvePublished(definition.getUserPromptVersionId()));
    }

    private static boolean shouldReturnEmptyKnowledgeHint(ModelChatContext context, AgentRuntimeDefinition definition) {
        return context.getRetrievalStatus() == RetrievalStatus.EMPTY
                && Objects.nonNull(definition.getRetrievalPolicy())
                && RETURN_EMPTY_HINT_STRATEGY.equals(definition.getRetrievalPolicy().getEmptyResultStrategy());
    }

    /**
     * 生成
     */
    private String generate(ChatExecutionContext execution, AgentRuntimeDefinition definition, PromptPair prompt, ModelChatContext context, ChatModelRuntimeConfig modelConfig, Duration timeout) {
        // 构建chat命令
        ChatStreamCommand command = ChatStreamCommand.builder()
                .prompt(prompt.userPrompt())
                .systemPrompt(applyToolSystemPolicy(prompt.systemPrompt(), definition))
                .history(context.getRuntimeVariables().require(RuntimeVariableAccessors.SESSION_HISTORY))
                .modelConfig(modelConfig)
                .toolRuntimeConfig(resolveToolRuntimeConfig(execution, definition).orElse(null))
                .build();

        StringBuilder answer = new StringBuilder();

        streamingChatGateway.stream(command)
                .doOnNext(chunk -> {
                    answer.append(chunk.content());
                    execution.recordUsage(chunk);
                    execution.getEventPublisher().publishText(chunk);
                })
                .then()
                .block(timeout);
        return answer.toString();
    }

    /**
     * 为启用工具的 Agent 追加平台级工具语义。
     *
     * <p>该规则必须位于 Agent Prompt 之后，明确工具结果是合法事实来源。
     * 普通 RAG Agent 不追加任何内容，保持已发布 Prompt 的原始行为。</p>
     */
    private static String applyToolSystemPolicy(String systemPrompt, AgentRuntimeDefinition definition) {
        if (definition.resolveToolCodes().isEmpty()) {
            return systemPrompt;
        }
        return Optional.ofNullable(systemPrompt)
                .filter(value -> !value.isBlank())
                .map(value -> value.trim() + "\n\n" + TOOL_AUGMENTED_SYSTEM_PROMPT)
                .orElse(TOOL_AUGMENTED_SYSTEM_PROMPT);
    }

    /**
     * 将 AgentVersion 中冻结的工具编码解析为本次请求的 Spring AI ToolCallback。
     */
    private Optional<AgentToolRuntimeConfig> resolveToolRuntimeConfig(ChatExecutionContext execution, AgentRuntimeDefinition definition) {
        if (definition.resolveToolCodes().isEmpty()) {
            return Optional.empty();
        }
        AgentRuntimeDefinition.ToolPolicy toolPolicy = definition.getToolPolicy();

        AgentRuntimeDefinition.RuntimePolicy runtimePolicy = definition.getRuntimePolicy();
        int rewriteCalls = rewriteEnabled(definition) ? 1 : 0;
        int generationModelCalls = runtimePolicy.maxModelCalls() - rewriteCalls;
        AgentToolExecutionContext toolExecutionContext = AgentToolExecutionContext.builder()
                .executionId(execution.getExecutionId())
                .userId(execution.getRuntimeVariables().require(RuntimeVariableAccessors.USER_ID))
                .conversationId(execution.getTurn().conversationId())
                .assistantMessageId(execution.getTurn().assistantMessageId())
                .build();

        AgentToolRuntimeConfig runtimeConfig = AgentToolRuntimeConfig.builder()
                .tools(toolRuntimeService.resolveActiveTools(definition.resolveToolCodes()))
                .executionContext(toolExecutionContext)
                .maxToolCalls(toolPolicy.maxToolCalls())
                .maxModelCalls(generationModelCalls)
                .maxResultCharacters(toolPolicy.maxResultCharacters())
                .eventPublisher(execution.getEventPublisher()::publishTool)
                .build();
        return Optional.of(runtimeConfig);
    }

    private static ChatModelRuntimeConfig applyGenerationPolicy(ChatModelRuntimeConfig source, AgentRuntimeDefinition.GenerationPolicy policy) {
        if (Objects.isNull(policy)) {
            return source;
        }
        Double temperature = source.getTemperature();
        if (Objects.nonNull(policy.getTemperature())) {
            temperature = policy.getTemperature().doubleValue();
        }
        Double topP = source.getTopP();
        if (Objects.nonNull(policy.getTopP())) {
            topP = policy.getTopP().doubleValue();
        }
        Integer maxTokens = Objects.requireNonNullElse(policy.getMaxOutputTokens(), source.getMaxTokens());
        Integer timeoutSeconds = Objects.requireNonNullElse(policy.getTimeoutSeconds(), source.getTimeoutSeconds());
        Integer maxRetries = Objects.requireNonNullElse(policy.getMaxRetries(), source.getMaxRetries());

        return ChatModelRuntimeConfig.builder().instanceId(source.getInstanceId()).baseUrl(source.getBaseUrl()).apiKey(source.getApiKey()).modelCode(source.getModelCode()).temperature(temperature).topP(topP).maxTokens(maxTokens).stopSequences(source.getStopSequences()).extensionParameters(source.getExtensionParameters()).timeoutSeconds(timeoutSeconds).maxRetries(maxRetries).build();
    }

    private static ChatModelRuntimeConfig applyRewritePolicy(ChatModelRuntimeConfig source, AgentRuntimeDefinition.RewritePolicy policy) {
        Double temperature = source.getTemperature();
        if (Objects.nonNull(policy.getTemperature())) {
            temperature = policy.getTemperature().doubleValue();
        }
        int timeoutSeconds = Math.max((policy.getTimeoutMillis() + 999) / 1000, 1);
        return ChatModelRuntimeConfig.builder().instanceId(source.getInstanceId()).baseUrl(source.getBaseUrl()).apiKey(source.getApiKey()).modelCode(source.getModelCode()).temperature(temperature).topP(source.getTopP()).maxTokens(policy.getMaxOutputTokens()).stopSequences(source.getStopSequences()).extensionParameters(source.getExtensionParameters()).timeoutSeconds(timeoutSeconds).maxRetries(source.getMaxRetries()).build();
    }

    private static Duration timeout(AgentRuntimeDefinition definition) {
        int totalTimeout = Optional.ofNullable(definition.getRuntimePolicy()).map(AgentRuntimeDefinition.RuntimePolicy::totalTimeoutSeconds).orElse(90);
        int generationTimeout = Optional.ofNullable(definition.getGenerationPolicy()).map(AgentRuntimeDefinition.GenerationPolicy::getTimeoutSeconds).orElse(60);
        int seconds = Math.min(totalTimeout, generationTimeout);
        return Duration.ofSeconds(Math.max(seconds, 1));
    }

    private static boolean rewriteEnabled(AgentRuntimeDefinition definition) {
        return Objects.nonNull(definition.getRewritePolicy()) && Boolean.TRUE.equals(definition.getRewritePolicy().getEnabled());
    }

    private static void validateSupported(AgentRuntimeDefinition definition) {
        if (!"RAG_CHAT".equals(definition.getAgentType())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "当前运行时仅支持RAG_CHAT Agent");
        }
        if (Objects.isNull(definition.getChatModelInstanceId()) || Objects.isNull(definition.resolveSystemPromptVersionId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion缺少模型或SYSTEM Prompt");
        }
        if (Objects.nonNull(definition.getSchemaVersion()) && definition.getSchemaVersion() >= 2 && Objects.isNull(definition.getUserPromptVersionId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion缺少USER Prompt");
        }
        AgentRuntimeDefinition.KnowledgeBinding knowledgeBinding = definition.getKnowledge();
        if (Objects.isNull(knowledgeBinding) || Objects.isNull(knowledgeBinding.knowledgeBaseId()) || Objects.isNull(knowledgeBinding.embeddingModelInstanceId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion知识库配置不完整");
        }
        AgentRuntimeDefinition.RetrievalPolicy retrievalPolicy = definition.getRetrievalPolicy();
        if (Objects.isNull(retrievalPolicy) || Objects.isNull(retrievalPolicy.getTopK()) || Objects.isNull(retrievalPolicy.getSimilarityThreshold()) || Objects.isNull(retrievalPolicy.getFailureStrategy())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion检索策略不完整");
        }
        validateRewritePolicy(definition);
        validateToolPolicy(definition);
    }

    private static void validateRewritePolicy(AgentRuntimeDefinition definition) {
        AgentRuntimeDefinition.RewritePolicy rewritePolicy = definition.getRewritePolicy();
        if (Objects.isNull(rewritePolicy) || !Boolean.TRUE.equals(rewritePolicy.getEnabled())) {
            return;
        }
        if (Objects.isNull(definition.getRewriteModelInstanceId()) || Objects.isNull(definition.getRewritePromptVersionId()) || Objects.isNull(rewritePolicy.getMaxHistoryRounds()) || Objects.isNull(rewritePolicy.getMaxHistoryTokens()) || Objects.isNull(rewritePolicy.getTemperature()) || Objects.isNull(rewritePolicy.getMaxOutputTokens()) || Objects.isNull(rewritePolicy.getMaxOutputCharacters()) || Objects.isNull(rewritePolicy.getTimeoutMillis()) || Objects.isNull(rewritePolicy.getFallbackToOriginal())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion问题改写配置不完整");
        }
    }

    private static void validateToolPolicy(AgentRuntimeDefinition definition) {
        if (definition.resolveToolCodes().isEmpty()) {
            return;
        }
        if (Objects.isNull(definition.getToolPolicy()) || Objects.isNull(definition.getToolPolicy().maxToolCalls()) || Objects.isNull(definition.getToolPolicy().maxResultCharacters()) || Objects.isNull(definition.getRuntimePolicy()) || Objects.isNull(definition.getRuntimePolicy().maxModelCalls())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion工具调用配置不完整");
        }
        AgentRuntimeDefinition.ToolPolicy toolPolicy = definition.getToolPolicy();
        int maxToolCalls = toolPolicy.maxToolCalls();
        int maxResultCharacters = toolPolicy.maxResultCharacters();
        if (maxToolCalls < MIN_TOOL_CALLS || maxToolCalls > MAX_TOOL_CALLS || maxResultCharacters < MIN_RESULT_CHARACTERS || maxResultCharacters > MAX_RESULT_CHARACTERS) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion工具调用配置超出允许范围");
        }
        int rewriteCalls = rewriteEnabled(definition) ? 1 : 0;
        int requiredModelCalls = rewriteCalls + maxToolCalls + 1;
        if (definition.getRuntimePolicy().maxModelCalls() < requiredModelCalls) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion模型调用次数不足以完成工具调用闭环");
        }
    }
}

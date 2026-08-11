package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.service.AgentChatService;
import com.lingxi.workbench.application.runtime.service.AgentRuntimeService;
import com.lingxi.workbench.application.runtime.service.ToolRuntimeService;
import com.lingxi.workbench.application.runtime.tool.AgentDelegationTool;
import com.lingxi.workbench.application.runtime.tool.KnowledgeSearchAgentToolFactory;
import com.lingxi.workbench.chat.model.bo.PromptPair;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.StreamingChatGateway;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.lingxi.workbench.application.runtime.common.constants.AgentChatConstants.DEFAULT_DELEGATION_RESULT_CHARACTERS;
import static com.lingxi.workbench.application.runtime.common.constants.AgentChatConstants.DELEGATE_AGENT_TOOL_PREFIX;
import static com.lingxi.workbench.application.runtime.common.constants.AgentChatConstants.SUPERVISOR_COLLABORATION_PROMPT;
import static com.lingxi.workbench.application.runtime.common.constants.AgentChatConstants.TOOL_AUGMENTED_SYSTEM_PROMPT;
import static com.lingxi.workbench.infra.ai.tool.common.constants.AgentToolLimitConstants.MAX_RESULT_CHARACTERS;
import static com.lingxi.workbench.infra.ai.tool.common.constants.AgentToolLimitConstants.MIN_RESULT_CHARACTERS;
import static com.lingxi.workbench.infra.ai.tool.common.constants.AgentToolLimitConstants.MIN_TOOL_CALLS;

/**
 * Agent 对话主流程。
 *
 * <p>这是阅读 Agent 运行时的主入口，执行顺序固定为：</p>
 *
 * <ol>
 *     <li>加载并校验发布后的 AgentVersion；</li>
 *     <li>根据版本配置投影共享上下文并构建模型请求；</li>
 *     <li>装配回答模型与工具配置；</li>
 *     <li>调用模型并汇总最终回答和 Token 用量。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class AgentChatServiceImpl implements AgentChatService {

    private static final String FINALIZATION_ONLY_PROMPT = "运行工作预算已到边界，本次不再开放工具、委派或规划。请基于会话中已有事实直接回答；信息不足时只提出一个最关键的补充问题。";

    private final AgentRuntimeService agentRuntimeService;
    private final AgentChatPreparationStrategyRegistry preparationStrategyRegistry;
    private final ModelRuntimeConfigService modelRuntimeConfigService;
    private final StreamingChatGateway streamingChatGateway;
    private final ToolRuntimeService toolRuntimeService;
    private final KnowledgeSearchAgentToolFactory knowledgeSearchToolFactory;

    /**
     * Agent 对话主流程。
     *
     * <ol>
     *   <li>resolveVersion → 加载并校验发布后的 AgentVersion</li>
     *   <li>prepare → 根据 agentType 选择策略，投影共享上下文构建 ModelChatContext</li>
     *   <li>loadRuntimeTools → 按发布快照装配可调用工具列表</li>
     *   <li>generate → StreamingChatGateway 流式调用 + Token 用量累加</li>
     * </ol>
     */
    @Override
    public String chat(Long agentId, Long agentVersionId, String query, ChatExecutionContext execution) {
        // ① 解析 Agent 发布快照（冻结不可变）
        AgentRuntimeSnapshot snapshot = agentRuntimeService.resolveVersion(agentId, agentVersionId);
        AgentRuntimeDefinition definition = snapshot.definition();
        // ② 通用校验
        validateCommon(definition);
        // ③ 校验 query 与运行时变量中的 USER_QUERY 一致
        validateEntryQuery(query, execution);
        // ④ Supervisor 协作变量注入（仅根调用生效）
        prepareCollaborationVariables(agentId, execution);

        // ⑤ 准备模型上下文：Prompt 渲染 + 变量替换
        ModelChatContext chatContext = preparationStrategyRegistry
                .require(definition.getAgentType())
                .prepare(definition, execution);
        // ⑥ 加载运行时工具：内置工具 + 发布快照中配置的外部工具 + 知识库搜索工具
        List<AgentTool> runtimeTools = this.loadRuntimeTools(execution, snapshot);

        PromptPair prompt = chatContext.getPromptPair();
        Optional<AgentToolRuntimeConfig> toolRuntimeConfig = resolveToolRuntimeConfig(execution, definition, runtimeTools);
        boolean finalizationOnly = !runtimeTools.isEmpty() && toolRuntimeConfig.isEmpty();
        int remainingTokenBudget = execution.shouldFinalize()
                ? execution.remainingFinalizationTokenBudget()
                : execution.remainingTokenBudget();
        ChatModelRuntimeConfig sourceConfig = modelRuntimeConfigService.getChatModelConfig(
                definition.getChatModelInstanceId());
        Duration requestTimeout = execution.remainingTimeout(Duration.ofSeconds(
                Optional.ofNullable(sourceConfig.getTimeoutSeconds())
                        .orElse(execution.getRuntimePolicy().getTimeoutSeconds())));
        ChatModelRuntimeConfig modelConfig = applyGenerationPolicy(
                sourceConfig, definition.getGenerationPolicy(), execution.getContextBudget(), remainingTokenBudget,
                requestTimeout);
        return this.generate(execution, prompt, chatContext, modelConfig, runtimeTools, toolRuntimeConfig,
                finalizationOnly, requestTimeout);
    }

    /**
     * 校验方法参数和当前 Agent 变量作用域中的查询一致。
     *
     * <p>入口作用域保存用户原始问题；成员 Agent 的隔离作用域保存 Supervisor 下发的
     * 子任务。两者都不能由调用方在执行中途静默覆盖。</p>
     */
    private static void validateEntryQuery(String query, ChatExecutionContext execution) {
        String originalQuery = execution.getRuntimeVariables()
                .require(RuntimeVariableAccessors.USER_QUERY);
        if (!originalQuery.equals(query)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Agent查询与运行时变量作用域不一致");
        }
    }

    /**
     * 从Application发布快照注入仅属于Supervisor根调用的协作变量。
     *
     * <p>成员Agent的隔离变量容器不会继承该变量，防止普通Agent Prompt误用
     * Supervisor的委派额度。</p>
     */
    private static void prepareCollaborationVariables(Long agentId, ChatExecutionContext execution) {
        execution.collaborationScope()
                .filter(scope -> scope.canDelegate(agentId))
                .ifPresent(scope -> execution.getRuntimeVariables().put(
                        RuntimeVariableAccessors.COLLABORATION_MAX_DELEGATIONS,
                        scope.maxDelegations()));
    }

    /**
     * 生成
     */
    private String generate(ChatExecutionContext execution, PromptPair prompt, ModelChatContext context,
                            ChatModelRuntimeConfig modelConfig, List<AgentTool> runtimeTools,
                            Optional<AgentToolRuntimeConfig> toolRuntimeConfig, boolean finalizationOnly,
                            Duration requestTimeout) {
        List<AgentTool> exposedTools = toolRuntimeConfig.isPresent() ? runtimeTools : List.of();
        String systemPrompt = applyToolSystemPolicy(prompt.systemPrompt(), exposedTools);
        if (finalizationOnly) {
            systemPrompt = systemPrompt.isBlank()
                    ? FINALIZATION_ONLY_PROMPT
                    : systemPrompt + "\n\n" + FINALIZATION_ONLY_PROMPT;
        }
        // 构建chat命令
        ChatStreamCommand command = ChatStreamCommand.builder()
                .prompt(prompt.userPrompt())
                .systemPrompt(systemPrompt)
                .history(context.getRuntimeVariables().require(RuntimeVariableAccessors.SESSION_HISTORY))
                .modelConfig(modelConfig)
                .toolRuntimeConfig(toolRuntimeConfig.orElse(null))
                .build();

        StringBuilder answer = new StringBuilder();

        if (toolRuntimeConfig.isEmpty()) {
            execution.reserveFinalModelCall();
        }
        streamingChatGateway.stream(command)
                .doOnNext(chunk -> {
                    answer.append(chunk.content());
                    execution.recordUsage(chunk);
                    execution.getEventPublisher().publishText(chunk);
                })
                .then()
                .block(requestTimeout);
        return answer.toString();
    }

    /**
     * 为启用工具的 Agent 追加平台级工具语义。
     *
     * <p>该规则必须位于 Agent Prompt 之后，明确工具结果是合法事实来源。
     * 未启用工具时保持已发布 Prompt 的原始行为。</p>
     */
    private static String applyToolSystemPolicy(String systemPrompt, List<AgentTool> runtimeTools) {
        if (runtimeTools.isEmpty()) {
            return systemPrompt;
        }
        String base = Optional.ofNullable(systemPrompt)
                .filter(value -> !value.isBlank())
                .map(value -> value.trim() + "\n\n" + TOOL_AUGMENTED_SYSTEM_PROMPT)
                .orElse(TOOL_AUGMENTED_SYSTEM_PROMPT);
        boolean collaborationEnabled = runtimeTools.stream().anyMatch(tool -> tool.definition().getCode().startsWith(DELEGATE_AGENT_TOOL_PREFIX));
        return collaborationEnabled ? base + "\n\n" + SUPERVISOR_COLLABORATION_PROMPT : base;
    }

    /**
     * 合并 Agent 自身工具与 Application 动态成员 Agent 工具。
     */
    private List<AgentTool> loadRuntimeTools(ChatExecutionContext execution, AgentRuntimeSnapshot snapshot) {
        List<AgentTool> tools = new ArrayList<>();
        if (!snapshot.definition().resolveToolCodes().isEmpty()) {
            tools.addAll(toolRuntimeService.resolveActiveTools(snapshot.definition().resolveToolCodes()));
        }
        if (Objects.nonNull(snapshot.definition().getKnowledgeSearch())) {
            tools.add(knowledgeSearchToolFactory.create(snapshot.definition().getKnowledgeSearch()));
        }
        execution.collaborationScope()
                .filter(scope -> scope.canDelegate(snapshot.agentId()))
                .stream()
                .flatMap(scope -> scope.members().stream())
                .map(member -> delegationTool(execution, member))
                .forEach(tools::add);
        return List.copyOf(tools);
    }

    private AgentTool delegationTool(ChatExecutionContext execution, ApplicationAgentReference member) {
        return new AgentDelegationTool(member, input -> {
            ChatExecutionContext child = execution.forkForDelegation(member, input.getTask());
            return chat(member.agentId(), member.agentVersionId(), input.getTask(), child);
        });
    }

    /** 将本次已解析的全部工具装配为 Spring AI 工具运行配置。 */
    private Optional<AgentToolRuntimeConfig> resolveToolRuntimeConfig(ChatExecutionContext execution,
                                                                      AgentRuntimeDefinition definition,
                                                                      List<AgentTool> runtimeTools) {
        if (runtimeTools.isEmpty()) {
            return Optional.empty();
        }
        int generationModelCalls = execution.remainingModelCalls();
        int maxToolCalls = execution.remainingToolCalls();
        if (execution.shouldFinalize() || maxToolCalls < MIN_TOOL_CALLS) {
            return Optional.empty();
        }
        int maxResultCharacters = Optional.ofNullable(definition.getToolPolicy())
                .map(AgentRuntimeDefinition.ToolPolicy::maxResultCharacters)
                .orElse(DEFAULT_DELEGATION_RESULT_CHARACTERS);
        AgentToolExecutionContext toolExecutionContext = AgentToolExecutionContext.builder()
                .executionId(execution.getExecutionId())
                .userId(execution.getRuntimeVariables().require(RuntimeVariableAccessors.USER_ID))
                .conversationId(execution.getTurn().conversationId())
                .assistantMessageId(execution.getTurn().assistantMessageId())
                .build();

        AgentToolRuntimeConfig runtimeConfig = AgentToolRuntimeConfig.builder()
                .tools(runtimeTools)
                .executionContext(toolExecutionContext)
                .maxToolCalls(maxToolCalls)
                .maxModelCalls(generationModelCalls)
                .maxTotalTokens(execution.remainingTokenBudget())
                .maxFinalizationTokens(execution.remainingFinalizationTokenBudget())
                .maxResultCharacters(maxResultCharacters)
                .eventPublisher(execution.getEventPublisher()::publishTool)
                .executionBudget(execution)
                .build();
        return Optional.of(runtimeConfig);
    }

    private static ChatModelRuntimeConfig applyGenerationPolicy(ChatModelRuntimeConfig source,
                                                                 AgentRuntimeDefinition.GenerationPolicy policy,
                                                                 ContextBudgetPolicy contextBudget,
                                                                 int remainingTokenBudget,
                                                                 Duration requestTimeout) {
        Double temperature = source.getTemperature();
        if (Objects.nonNull(policy) && Objects.nonNull(policy.getTemperature())) {
            temperature = policy.getTemperature().doubleValue();
        }
        Double topP = source.getTopP();
        if (Objects.nonNull(policy) && Objects.nonNull(policy.getTopP())) {
            topP = policy.getTopP().doubleValue();
        }
        int outputBudget = Math.min(contextBudget.getReservedOutputTokens(), remainingTokenBudget);
        int maxTokens = Objects.isNull(source.getMaxTokens())
                ? outputBudget
                : Math.min(source.getMaxTokens(), outputBudget);

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
                .timeoutSeconds(timeoutSeconds(requestTimeout))
                .maxRetries(source.getMaxRetries())
                .build();
    }

    /** 模型配置只接受整秒，向上取整；流式阻塞仍使用精确剩余时间作为硬截止。 */
    private static int timeoutSeconds(Duration timeout) {
        long seconds = timeout.toSeconds();
        if (!timeout.minusSeconds(seconds).isZero()) {
            seconds++;
        }
        return Math.toIntExact(Math.max(1L, seconds));
    }

    private static void validateCommon(AgentRuntimeDefinition definition) {
        if (Objects.isNull(definition.getChatModelInstanceId())
                || Objects.isNull(definition.getSystemPromptVersionId())
                || Objects.isNull(definition.getUserPromptVersionId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion缺少模型、SYSTEM Prompt或USER Prompt");
        }
        validateToolPolicy(definition);
    }

    private static void validateToolPolicy(AgentRuntimeDefinition definition) {
        if (definition.resolveToolCodes().isEmpty() && Objects.isNull(definition.getKnowledgeSearch())) {
            return;
        }
        AgentRuntimeDefinition.ToolPolicy toolPolicy = definition.getToolPolicy();
        if (Objects.nonNull(toolPolicy) && (Objects.isNull(toolPolicy.maxResultCharacters())
                || toolPolicy.maxResultCharacters() < MIN_RESULT_CHARACTERS
                || toolPolicy.maxResultCharacters() > MAX_RESULT_CHARACTERS)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion工具结果字符上限超出允许范围");
        }
        // 工具授权和单次结果裁剪属于Agent行为；调用次数、Token与超时统一由Application控制。
    }
}

package com.lingxi.workbench.application.runtime.service.agent;

import com.lingxi.workbench.agentruntime.AgentRuntime;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpointContext;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpointStore;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.model.AgentExecutionDeadlineExceededException;
import com.lingxi.workbench.agentruntime.model.AgentResumeRequest;
import com.lingxi.workbench.agentruntime.model.AgentRunRequest;
import com.lingxi.workbench.agentruntime.model.AgentRunResult;
import com.lingxi.workbench.agentruntime.model.ExecutionPolicy;
import com.lingxi.workbench.agentruntime.model.SkillRuntimeCatalog;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.prompt.PromptRuntimeService;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionPersistenceService;
import com.lingxi.workbench.application.runtime.service.execution.ApplicationRuntimeEventSinkFactory;
import com.lingxi.workbench.application.runtime.service.skill.SkillRuntimeService;
import com.lingxi.workbench.application.runtime.service.tool.ToolRuntimeService;
import com.lingxi.workbench.application.runtime.tool.KnowledgeSearchAgentToolFactory;
import com.lingxi.workbench.chat.model.bo.PromptPair;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.service.impl.ModelRequestFactory;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.lingxi.workbench.application.runtime.common.constants.AgentChatConstants.DEFAULT_DELEGATION_RESULT_CHARACTERS;
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
    private final AgentRuntime agentRuntime;
    private final PromptRuntimeService promptRuntimeService;
    private final ModelRequestFactory modelRequestFactory;
    private final ModelRuntimeConfigService modelRuntimeConfigService;
    private final ToolRuntimeService toolRuntimeService;
    private final KnowledgeSearchAgentToolFactory knowledgeSearchToolFactory;
    private final RuntimeCheckpointStore checkpointStore;
    private final SkillRuntimeService skillRuntimeService;
    private final ExecutionPersistenceService executionPersistenceService;
    private final ApplicationRuntimeEventSinkFactory runtimeEventSinkFactory;

    /**
     * Agent 对话主流程。
     *
     * <ol>
     *   <li>resolveVersion → 加载并校验发布后的 AgentVersion</li>
     *   <li>prepare → 根据 role 选择策略，投影共享上下文构建 ModelChatContext</li>
     *   <li>loadRuntimeTools → 按发布快照装配可调用工具列表</li>
     *   <li>generate → 将锁定版本与可信上下文提交给 AgentRuntime</li>
     * </ol>
     */
    @Override
    public AgentRunResult chat(Long agentId, Long agentVersionId, String query, ChatExecutionContext execution) {
        AgentRuntimeSnapshot snapshot = agentRuntimeService.resolveVersion(agentId, agentVersionId);
        return chat(snapshot, query, execution);
    }

    /**
     * 执行一次 Agent 对话（基于已解析的发布快照）。
     *
     * <p>这是 Agent 执行循环的编排入口，按固定顺序完成：通用校验 → 入口查询一致性校验 →
     * Supervisor 协作变量注入 → 按 role 选择策略准备模型上下文 → 加载运行时工具 →
     * 推导生成策略与剩余预算 → 调用模型生成回答。若共享预算已到边界（finalizationOnly），
     * 本次调用只做最终回答，不再开放工具。</p>
     *
     * @param snapshot  已解析并校验通过的 Agent 发布快照
     * @param query     用户原始问题，必须与运行时变量中的 USER_QUERY 一致
     * @param execution 共享执行上下文（预算、事件、协作作用域、运行时变量）
     * @return 模型生成的最终回答文本
     */
    @Override
    public AgentRunResult chat(AgentRuntimeSnapshot snapshot, String query, ChatExecutionContext execution) {
        AgentRuntimeDefinition definition = snapshot.definition();
        // ② 通用校验
        validateCommon(definition);
        // ③ 校验 query 与运行时变量中的 USER_QUERY 一致
        validateEntryQuery(query, execution);
        // ④ 只激活AgentVersion锁定且仍通过安全熔断的SkillVersion
        SkillRuntimeCatalog skillCatalog = skillRuntimeService.load(
                definition, execution.getContextBudget(), execution.getExplicitSkillCodes());
        // ⑤ 领域专家按发布快照渲染Prompt；主Agent必须走独立规划链，不能进入普通对话循环
        ModelChatContext chatContext = prepareSpecialist(definition, execution);
        // ⑥ 只装配领域专家自己的业务工具和知识搜索工具
        List<AgentTool> runtimeTools = this.loadRuntimeTools(snapshot);

        PromptPair prompt = chatContext.getPromptPair();
        Optional<AgentToolRuntimeConfig> toolRuntimeConfig = resolveToolRuntimeConfig(
                execution, snapshot, runtimeTools);
        // 预算边界判定：工具已装配但工具配置为空 ⇒ 进入收尾模式，本次只做最终回答、不再开放工具
        boolean finalizationOnly = !runtimeTools.isEmpty() && toolRuntimeConfig.isEmpty();
        // 收尾阶段改用收尾专用 Token 预算，其余情况使用常规剩余预算
        int remainingTokenBudget = execution.shouldFinalize()
                ? execution.remainingFinalizationTokenBudget()
                : execution.remainingTokenBudget();
        // 从模型实例配置出发，叠加 Agent 生成策略与剩余预算，得到本次请求的最终模型配置
        ChatModelRuntimeConfig sourceConfig = modelRuntimeConfigService.getChatModelConfig(
                definition.getChatModelInstanceId());
        Duration requestedModelTimeout = Duration.ofSeconds(
                Optional.ofNullable(sourceConfig.getTimeoutSeconds())
                        .orElse(execution.getRuntimePolicy().getTimeoutSeconds()
                                + execution.getRuntimePolicy().effectiveTimeoutGraceSeconds()));
        Duration hardRequestTimeout = execution.remainingTimeout(requestedModelTimeout);
        Duration requestTimeout = execution.remainingStreamingTimeout(requestedModelTimeout);
        ChatModelRuntimeConfig modelConfig = applyGenerationPolicy(
                sourceConfig, definition.getGenerationPolicy(), execution.getContextBudget(), remainingTokenBudget,
                requestTimeout);
        return this.generate(snapshot, execution, prompt, chatContext, modelConfig, skillCatalog,
                definition.resolveToolCodes(), toolRuntimeConfig, finalizationOnly, remainingTokenBudget,
                hardRequestTimeout, requestTimeout);
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
     * 构建领域专家的直接模型上下文。
     */
    private ModelChatContext prepareSpecialist(AgentRuntimeDefinition definition, ChatExecutionContext execution) {
        if (definition.getRole() != AgentRole.SPECIALIST) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "只有领域专家可以进入ReAct执行链");
        }
        PromptRuntimeSnapshot userPrompt = promptRuntimeService.resolvePublished(definition.getUserPromptVersionId());
        ModelChatContext context = ModelChatContext.builder()
                .runtimeVariables(execution.getRuntimeVariables())
                .systemPrompt(promptRuntimeService.resolvePublished(definition.getSystemPromptVersionId()))
                .userPrompt(userPrompt)
                .build();
        context.setPromptPair(modelRequestFactory.createDirectPrompt(context));
        return context;
    }

    /**
     * 生成
     */
    private AgentRunResult generate(AgentRuntimeSnapshot snapshot, ChatExecutionContext execution,
                                    PromptPair prompt, ModelChatContext context, ChatModelRuntimeConfig modelConfig,
                                    SkillRuntimeCatalog skillCatalog, List<String> authorizedToolCodes,
                                    Optional<AgentToolRuntimeConfig> toolRuntimeConfig, boolean finalizationOnly,
                                    int remainingTokenBudget, Duration hardRequestTimeout, Duration requestTimeout) {
        String systemPrompt = finalizationOnly
                ? prompt.systemPrompt() + "\n\n" + FINALIZATION_ONLY_PROMPT
                : prompt.systemPrompt();
        ChatStreamCommand command = ChatStreamCommand.builder()
                .prompt(prompt.userPrompt())
                .systemPrompt(systemPrompt)
                .history(context.getRuntimeVariables().require(RuntimeVariableAccessors.SESSION_HISTORY))
                .modelConfig(modelConfig)
                .toolRuntimeConfig(toolRuntimeConfig.orElse(null))
                .build();
        RuntimeEventSink eventSink = runtimeEventSinkFactory.create(execution);
        Duration activeOutputGrace = requestTimeout.compareTo(hardRequestTimeout) > 0
                ? requestTimeout.minus(hardRequestTimeout)
                : Duration.ZERO;
        com.lingxi.workbench.application.runtime.model.ExecutionResumeContext resume = execution.getResumeContext();
        boolean hasModelRoutedSkill = skillCatalog.bindings().stream()
                .anyMatch(binding -> binding.getSkill().getActivationMode()
                        == com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode.MODEL_ROUTED);
        int maxModelCalls = resume == null
                ? toolRuntimeConfig.isPresent() ? execution.remainingModelCalls()
                : hasModelRoutedSkill && !finalizationOnly ? Math.min(execution.remainingModelCalls(), 3) : 1
                : resume.getCheckpoint().getState().getMaxModelCalls();
        int maxToolCalls = resume == null
                ? toolRuntimeConfig.isPresent() ? execution.remainingToolCalls() : 0
                : resume.getCheckpoint().getState().getMaxToolCalls();
        ExecutionPolicy policy = resume == null
                ? ExecutionPolicy.builder()
                .maxModelCalls(maxModelCalls)
                .maxToolCalls(maxToolCalls)
                .maxTotalTokens(remainingTokenBudget)
                .maxFinalizationTokens(execution.remainingFinalizationTokenBudget())
                .timeout(hardRequestTimeout)
                .activeOutputGrace(activeOutputGrace)
                .build()
                : ExecutionPolicy.builder()
                .maxModelCalls(maxModelCalls)
                .maxToolCalls(maxToolCalls)
                .maxTotalTokens(resume.getCheckpoint().getState().getMaxTotalTokens())
                .maxFinalizationTokens(resume.getCheckpoint().getState().getMaxFinalizationTokens())
                .timeout(Duration.ofMillis(resume.getCheckpoint().getState().getTimeoutMillis()))
                .activeOutputGrace(Duration.ofMillis(
                        resume.getCheckpoint().getState().getActiveOutputGraceMillis()))
                .build();
        boolean approvalPossible = toolRuntimeConfig.stream()
                .flatMap(config -> config.getTools().stream())
                .anyMatch(tool -> tool.definition().getSecurityPolicy().isApprovalRequired());
        RuntimeCheckpointContext checkpointContext = resume != null
                ? resume.getCheckpoint().getContext()
                : approvalPossible ? RuntimeCheckpointContext.builder()
                .query(execution.getRuntimeVariables().require(RuntimeVariableAccessors.USER_QUERY))
                .capabilityCode(resolveCapabilityCode(execution, snapshot))
                .applicationVersionId(execution.getConversationContext().getApplicationSnapshot().versionId())
                .userId(execution.getRuntimeVariables().require(RuntimeVariableAccessors.USER_ID))
                .conversationId(execution.getTurn().conversationId())
                .assistantMessageId(execution.getTurn().assistantMessageId())
                .explicitSkillCodes(execution.getExplicitSkillCodes())
                .applicationUsageBeforeAgent(execution.tokenUsage())
                .applicationModelCallsBeforeAgent(execution.usedModelCalls())
                .applicationToolCallsBeforeAgent(execution.usedToolCalls())
                .applicationElapsedBeforeAgentMillis(execution.activeElapsedMillis())
                .build()
                : null;
        AgentRunRequest runRequest = AgentRunRequest.builder()
                .runId(execution.getExecutionId())
                .agentId(snapshot.agentId())
                .agentVersionId(snapshot.agentVersionId())
                .scope(execution.getExecutionScope())
                .modelCommand(command)
                .skillCatalog(skillCatalog)
                .authorizedToolCodes(authorizedToolCodes)
                .finalizationOnly(finalizationOnly)
                .policy(policy)
                .eventSink(eventSink)
                .initialSequenceNo(executionPersistenceService.lastEventSequence(execution.getExecutionId()))
                .initialModelCalls(0)
                .initialUsage(TokenUsage.empty())
                .checkpointContext(checkpointContext)
                .checkpointStore(checkpointStore)
                .build();
        AgentRunResult result = resume == null
                ? agentRuntime.run(runRequest)
                : agentRuntime.resume(new AgentResumeRequest(runRequest, resume.getCheckpoint(),
                resume.getInteractionId(), resume.getFingerprint()));
        if (result instanceof AgentRunResult.Partial) {
            throw new AgentExecutionDeadlineExceededException();
        }
        return result;
    }

    /**
     * 装配领域专家自身的业务工具和知识搜索工具。
     */
    private List<AgentTool> loadRuntimeTools(AgentRuntimeSnapshot snapshot) {
        List<AgentTool> tools = new ArrayList<>();
        if (!snapshot.definition().resolveToolCodes().isEmpty()) {
            tools.addAll(toolRuntimeService.resolveActiveTools(snapshot.definition().resolveToolCodes()));
        }
        if (Objects.nonNull(snapshot.definition().getKnowledgeSearch())) {
            tools.add(knowledgeSearchToolFactory.create(snapshot.definition().getKnowledgeSearch()));
        }
        return List.copyOf(tools);
    }

    /**
     * 将本次已解析的全部工具装配为 Spring AI 工具运行配置。
     */
    private Optional<AgentToolRuntimeConfig> resolveToolRuntimeConfig(ChatExecutionContext execution,
                                                                      AgentRuntimeSnapshot snapshot,
                                                                      List<AgentTool> runtimeTools) {
        AgentRuntimeDefinition definition = snapshot.definition();
        if (runtimeTools.isEmpty()) {
            return Optional.empty();
        }
        // 预算边界：已进入收尾阶段或剩余工具调用次数不足时，本次调用降级为纯文本回答
        int generationModelCalls = execution.remainingModelCalls();
        int maxToolCalls = execution.remainingToolCalls();
        if (execution.getResumeContext() == null
                && (execution.shouldFinalize() || maxToolCalls < MIN_TOOL_CALLS)) {
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
                .maxResultCharacters(maxResultCharacters)
                .build();
        return Optional.of(runtimeConfig);
    }

    private static String resolveCapabilityCode(ChatExecutionContext execution, AgentRuntimeSnapshot agent) {
        return execution.getConversationContext().getApplicationSnapshot().definition().capabilityCatalog()
                .capabilities().stream()
                .filter(capability -> capability.getType() == ApplicationCapabilityType.AGENT)
                .filter(capability -> Objects.equals(capability.getTargetId(), agent.agentId()))
                .filter(capability -> Objects.equals(capability.getTargetVersionId(), agent.agentVersionId()))
                .map(ApplicationCapabilityDefinition::getCode)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.CONFLICT, "当前Agent不在Application能力目录中"));
    }

    /**
     * 在模型实例基础配置上叠加 Agent 生成策略与上下文预算，得到本次请求的最终模型配置。
     *
     * <p>采样参数（temperature/topP）优先采用发布版本中显式配置的值；maxTokens 取模型配置、
     * 上下文预留输出 Token 与剩余 Token 预算三者的较小值，保证单次输出不会挤爆共享预算。
     * 超时统一向上取整为整秒模型配置，流式阻塞仍使用精确剩余时间作为硬截止。</p>
     *
     * @param source               模型实例的原始配置
     * @param policy               Agent 发布版本声明的生成策略，可为 null
     * @param contextBudget        上下文预算（预留输出 Token）
     * @param remainingTokenBudget 当前剩余 Token 预算
     * @param requestTimeout       本次请求的剩余超时时间
     * @return 叠加策略与预算后的最终模型配置
     */
    private static ChatModelRuntimeConfig applyGenerationPolicy(ChatModelRuntimeConfig source,
                                                                AgentRuntimeDefinition.GenerationPolicy policy,
                                                                ContextBudgetPolicy contextBudget,
                                                                int remainingTokenBudget,
                                                                Duration requestTimeout) {
        // 采样参数：发布版本显式配置的值优先于模型实例默认值
        Double temperature = source.getTemperature();
        if (Objects.nonNull(policy) && Objects.nonNull(policy.getTemperature())) {
            temperature = policy.getTemperature().doubleValue();
        }
        Double topP = source.getTopP();
        if (Objects.nonNull(policy) && Objects.nonNull(policy.getTopP())) {
            topP = policy.getTopP().doubleValue();
        }
        // 输出上限取「上下文预留输出 Token」与「剩余 Token 预算」的较小值，防止单次回答挤爆共享预算
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
                .structuredOutputSupported(source.isStructuredOutputSupported())
                .build();
    }

    /**
     * 模型配置只接受整秒，向上取整；流式阻塞仍使用精确剩余时间作为硬截止。
     */
    private static int timeoutSeconds(Duration timeout) {
        long seconds = timeout.toSeconds();
        if (!timeout.minusSeconds(seconds).isZero()) {
            seconds++;
        }
        return Math.toIntExact(Math.max(1L, seconds));
    }

    /**
     * 校验 AgentVersion 关键绑定的完整性。
     *
     * <p>模型实例、SYSTEM Prompt、USER Prompt 三者缺一不可，随后委托 validateToolPolicy
     * 校验工具相关配置。任一校验失败都以冲突状态拒绝本次执行。</p>
     *
     * @param definition Agent 运行时定义（发布快照内容）
     */
    private static void validateCommon(AgentRuntimeDefinition definition) {
        if (definition.getRole() != AgentRole.SPECIALIST
                || Objects.isNull(definition.getChatModelInstanceId())
                || Objects.isNull(definition.getSystemPromptVersionId())
                || Objects.isNull(definition.getUserPromptVersionId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion缺少模型、SYSTEM Prompt或USER Prompt");
        }
        validateToolPolicy(definition);
    }

    /**
     * 校验工具策略：配置了工具（内置工具或知识库搜索）时必须给出合法的单次结果裁剪上限。
     *
     * <p>结果字符上限必须在平台允许的 [MIN_RESULT_CHARACTERS, MAX_RESULT_CHARACTERS] 范围内；
     * 未配置任何工具时直接放行，不强制要求工具策略。</p>
     *
     * @param definition Agent 运行时定义（发布快照内容）
     */
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

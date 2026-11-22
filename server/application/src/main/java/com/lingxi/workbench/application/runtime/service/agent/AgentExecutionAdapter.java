package com.lingxi.workbench.application.runtime.service.agent;

import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.agentruntime.AgentRuntime;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpoint;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpointStore;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.model.AgentResumeRequest;
import com.lingxi.workbench.agentruntime.model.AgentRunRequest;
import com.lingxi.workbench.agentruntime.model.AgentRunResult;
import com.lingxi.workbench.agentruntime.model.ExecutionPolicy;
import com.lingxi.workbench.agentruntime.model.SkillRuntimeCatalog;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapability;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityInvoker;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityRisk;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityType;
import com.lingxi.workbench.agentruntime.plan.RuntimeClarification;
import com.lingxi.workbench.agentruntime.plan.SupervisorRuntimeConfig;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ApplicationExecutionBudget;
import com.lingxi.workbench.application.runtime.model.ApplicationRun;
import com.lingxi.workbench.application.runtime.model.PreResolvedDispatch;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.model.CapabilityInvocationScope;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.prompt.PromptRuntimeService;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionPersistenceService;
import com.lingxi.workbench.application.runtime.service.execution.ApplicationRuntimeEventSinkFactory;
import com.lingxi.workbench.application.runtime.service.execution.ApplicationRuntimeTaskStore;
import com.lingxi.workbench.application.runtime.service.interaction.InteractionSessionService;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.model.CapabilityToolDescriptor;
import com.lingxi.workbench.application.runtime.model.InteractionSessionContext;
import com.lingxi.workbench.application.runtime.model.InteractionSessionOpenCommand;
import com.lingxi.workbench.application.runtime.service.skill.SkillRuntimeService;
import com.lingxi.workbench.application.runtime.service.tool.ToolRuntimeService;
import com.lingxi.workbench.application.runtime.tool.KnowledgeSearchAgentToolFactory;
import com.lingxi.workbench.chat.model.bo.PromptPair;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.chat.service.impl.ModelRequestFactory;
import com.lingxi.workbench.chat.service.impl.PromptTemplateRenderer;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import com.lingxi.workbench.workflow.model.CapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;

import static com.lingxi.workbench.application.runtime.common.constants.AgentChatConstants.DEFAULT_DELEGATION_RESULT_CHARACTERS;
import static com.lingxi.workbench.infra.ai.prompt.common.constants.PromptSupplementConstants.FINALIZATION_ONLY_PROMPT;
import static com.lingxi.workbench.infra.ai.prompt.common.constants.PromptSupplementConstants.SUPERVISOR_PLATFORM_CONTRACT;
import static com.lingxi.workbench.infra.ai.tool.common.constants.AgentToolLimitConstants.MAX_RESULT_CHARACTERS;
import static com.lingxi.workbench.infra.ai.tool.common.constants.AgentToolLimitConstants.MIN_RESULT_CHARACTERS;
import static com.lingxi.workbench.infra.ai.tool.common.constants.AgentToolLimitConstants.MIN_TOOL_CALLS;

/**
 * Application 到 AgentRuntime 的唯一执行适配器。
 *
 * <p>本组件只负责发布 Prompt、Model、Tool、Skill 与预算的有效合并，
 * 然后将强类型请求提交给 AgentRuntime；它不构建或暴露 AgentScope RuntimeContext。</p>
 */
@Service
@RequiredArgsConstructor
public class AgentExecutionAdapter {

    private static final String CAPABILITY_PREFIX = "\n\n<capability_catalog_untrusted>\n";
    private static final String CAPABILITY_SUFFIX = "\n</capability_catalog_untrusted>";

    private final AgentVersionService agentVersionService;
    private final AgentRuntime agentRuntime;
    private final PromptRuntimeService promptRuntimeService;
    private final ModelRequestFactory modelRequestFactory;
    private final ModelRuntimeConfigService modelRuntimeConfigService;
    private final ToolRuntimeService toolRuntimeService;
    private final KnowledgeSearchAgentToolFactory knowledgeSearchToolFactory;
    private final AgentScopeCheckpointStore checkpointStore;
    private final SkillRuntimeService skillRuntimeService;
    private final ExecutionPersistenceService executionPersistenceService;
    private final ApplicationRuntimeEventSinkFactory runtimeEventSinkFactory;
    private final ApplicationRuntimeTaskStore runtimeTaskStore;
    private final InteractionSessionService interactionSessionService;
    private final PromptTemplateRenderer promptTemplateRenderer;
    private final TokenCountEstimator tokenCountEstimator;

    /**
     * 将已完成版本锁定的 Specialist 能力请求提交给 Agent Runtime。
     */
    public CapabilityExecutionResult executeSpecialist(CapabilityExecutionRequest request, AgentRuntimeSnapshot snapshot) {
        AgentRunResult result = run(snapshot, request.getInputs().getString("query"), request.getRun(),
                request.getInvocationScope());
        if (result instanceof AgentRunResult.Interrupted interrupted) {
            return new CapabilityExecutionResult("", null, List.of(), interrupted.interruption());
        }
        String answer = result.content();
        if (answer == null || answer.isBlank()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Agent能力必须返回非空结果");
        }
        return new CapabilityExecutionResult(answer, snapshot.definition().getChatModelInstanceId(), List.of());
    }

    /**
     * 将已完成版本锁定的 Supervisor 请求提交给统一 Agent Runtime。
     */
    public CapabilityExecutionResult executeSupervisor(CapabilityExecutionRequest request,
                                                       AgentRuntimeSnapshot snapshot,
                                                       RuntimeCapabilityInvoker capabilityInvoker) {
        validateSupervisor(request, snapshot, capabilityInvoker);
        PreparedSupervisor prepared = prepareSupervisor(request, snapshot);
        ApplicationRun run = request.getRun();
        ApplicationExecutionBudget executionBudget = run.budget();
        ChatStreamCommand command = ChatStreamCommand.builder()
                .prompt(request.getInputs().getString("query"))
                .systemPrompt(prepared.systemPrompt())
                .history(prepared.history())
                .modelConfig(prepared.modelConfig())
                .build();
        AgentRunResult result = agentRuntime.run(AgentRunRequest.builder()
                .runId(run.executionId())
                .agentId(snapshot.agentId())
                .agentVersionId(snapshot.agentVersionId())
                .role(AgentRole.SUPERVISOR)
                .scope(request.executionScope())
                .modelCommand(command)
                .userId(String.valueOf(run.request().getLoginUser().id()))
                .sessionId(request.agentSessionId())
                .policy(supervisorExecutionPolicy(run))
                .eventSink(runtimeEventSinkFactory.create(executionBudget, request.eventPublisher()))
                .initialSequenceNo(executionPersistenceService.lastEventSequence(run.executionId()))
                .initialModelCalls(0)
                .initialUsage(TokenUsage.empty())
                .supervisorConfig(SupervisorRuntimeConfig.builder()
                        .planPolicy(snapshot.definition().getPlanPolicy())
                        .maxDelegations(executionBudget.remainingDelegations())
                        .capabilities(prepared.runtimeCapabilities())
                        .confirmedCapabilityCodes(request.confirmedCapabilityCodes())
                        .capabilityInvoker(capabilityInvoker)
                        .taskStore(runtimeTaskStore)
                        .build())
                .build());
        executionBudget.recordUsage(result.usage());
        if (result instanceof AgentRunResult.Clarification clarification) {
            openClarification(request, clarification.clarification(), snapshot);
            return new CapabilityExecutionResult(clarification.content(), null, List.of());
        }
        if (result instanceof AgentRunResult.Interrupted interrupted) {
            return new CapabilityExecutionResult("", null, List.of(), interrupted.interruption());
        }
        return new CapabilityExecutionResult(result.content(), null, List.of());
    }

    private PreparedSupervisor prepareSupervisor(CapabilityExecutionRequest request,
                                                 AgentRuntimeSnapshot snapshot) {
        ApplicationRun run = request.getRun();
        ConversationContext context = run.conversation();
        AgentRuntimeDefinition definition = snapshot.definition();
        PromptRuntimeSnapshot prompt = promptRuntimeService.resolvePublished(definition.getSystemPromptVersionId());
        RuntimeVariables invocationVariables = context.getRuntimeVariables()
                .forkForQuery(request.getInputs().getString("query"));
        String renderedPrompt = promptTemplateRenderer.renderSystemPrompt(prompt, invocationVariables).trim();
        Map<String, CapabilityDefinition> capabilityIndex = context.getApplicationSnapshot().definition()
                .capabilityCatalog().capabilities().stream()
                .filter(capability -> !Objects.equals(capability.getCode(), request.getCapability().getCode()))
                .collect(Collectors.toUnmodifiableMap(CapabilityDefinition::getCode,
                        capability -> capability));
        String capabilityJson = escapeClosingTags(JSON.toJSONString(capabilityIndex.values().stream()
                .map(AgentExecutionAdapter::descriptor).toList()));
        String routingEvidenceJson = escapeClosingTags(JSON.toJSONString(request.getRoutingEvidence()));
        validateSupervisorBudgets(context, renderedPrompt, capabilityJson + routingEvidenceJson);
        String systemPrompt = renderedPrompt + SUPERVISOR_PLATFORM_CONTRACT + CAPABILITY_PREFIX + capabilityJson
                + CAPABILITY_SUFFIX + routingEvidenceSection(routingEvidenceJson);
        List<ChatHistoryMessage> history = invocationVariables
                .require(RuntimeVariableAccessors.SESSION_HISTORY);
        int estimatedInputTokens = estimateSupervisorInput(systemPrompt,
                request.getInputs().getString("query"), history);
        int maxOutputTokens = Math.min(context.getBudget().getReservedOutputTokens(),
                Math.max(run.budget().remainingFinalizationTokenBudget()
                        - estimatedInputTokens, 1));
        ChatModelRuntimeConfig source = modelRuntimeConfigService.getChatModelConfig(
                definition.getChatModelInstanceId());
        int responseIdleTimeoutSeconds = Objects.requireNonNull(
                source.getTimeoutSeconds(), "模型响应空闲超时不能为空");
        ChatModelRuntimeConfig modelConfig = applyGenerationPolicy(source, definition.getGenerationPolicy(),
                context.getBudget(), maxOutputTokens, responseIdleTimeoutSeconds);
        List<RuntimeCapability> capabilities = capabilityIndex.values().stream()
                .map(AgentExecutionAdapter::runtimeCapability).toList();
        return new PreparedSupervisor(systemPrompt, history, modelConfig, capabilities);
    }

    private void openClarification(CapabilityExecutionRequest request, RuntimeClarification clarification,
                                   AgentRuntimeSnapshot snapshot) {
        InteractionSessionContext sessionContext = InteractionSessionContext.builder()
                .clarification(Objects.requireNonNull(clarification, "Supervisor澄清内容不能为空"))
                .capabilityCodes(List.of())
                .originalQuery(request.getRun().request().getQuery())
                .build();
        request.eventPublisher().publishClarification(
                interactionSessionService.open(InteractionSessionOpenCommand.builder()
                        .application(request.getRun().request().getApplication())
                        .turn(request.getRun().request().getTurn())
                        .userId(request.getRun().request().getLoginUser().id())
                        .runId(request.getRun().executionId())
                        .resumeCapabilityCode(request.getCapability().getCode())
                        .orchestratorAgentId(snapshot.agentId())
                        .orchestratorAgentVersionId(snapshot.agentVersionId())
                        .interactionType(InteractionSessionType.CLARIFICATION)
                        .context(sessionContext)
                        .build()));
    }

    private static ExecutionPolicy supervisorExecutionPolicy(ApplicationRun run) {
        ApplicationExecutionBudget budget = run.budget();
        int workingTokens;
        try {
            workingTokens = budget.remainingTokenBudget();
        } catch (BusinessException exception) {
            workingTokens = 1;
        }
        return ExecutionPolicy.builder().maxModelCalls(Math.max(budget.remainingModelCalls(), 1))
                .maxToolCalls(budget.remainingToolCalls()).maxTotalTokens(workingTokens)
                .maxFinalizationTokens(Math.max(budget.remainingFinalizationTokenBudget(), workingTokens)).build();
    }

    private static CapabilityToolDescriptor descriptor(CapabilityDefinition capability) {
        return CapabilityToolDescriptor.builder().code(capability.getCode()).type(capability.getType())
                .name(capability.getName()).description(capability.getDescription())
                .inputSchema(capability.getInputSchema()).outputSchema(capability.getOutputSchema())
                .riskLevel(capability.getRiskLevel()).build();
    }

    private static RuntimeCapability runtimeCapability(CapabilityDefinition capability) {
        return RuntimeCapability.builder().code(capability.getCode()).name(capability.getName())
                .description(capability.getDescription())
                .type(RuntimeCapabilityType.valueOf(capability.getType().name())).targetId(capability.getTargetId())
                .risk(RuntimeCapabilityRisk.valueOf(capability.getRiskLevel().name()))
                .maxCalls(capability.getMaxCalls()).resultTokenBudget(capability.getResultTokenBudget()).build();
    }

    private void validateSupervisorBudgets(ConversationContext context, String systemPrompt,
                                           String capabilityJson) {
        if (tokenCountEstimator.estimate(systemPrompt + SUPERVISOR_PLATFORM_CONTRACT)
                > context.getBudget().getMaxSystemPromptTokens()) {
            throw invalid("Planner系统提示词超过Application上下文预算");
        }
        if (tokenCountEstimator.estimate(capabilityJson) > context.getBudget().getMaxCapabilityTokens()) {
            throw invalid("Planner能力目录超过Application上下文预算");
        }
    }

    private int estimateSupervisorInput(String systemPrompt, String query, List<ChatHistoryMessage> history) {
        int tokens = tokenCountEstimator.estimate(systemPrompt) + tokenCountEstimator.estimate(query);
        for (ChatHistoryMessage message : history) {
            tokens += tokenCountEstimator.estimate(message.content());
        }
        return tokens;
    }

    private static String routingEvidenceSection(String evidenceJson) {
        return evidenceJson == null || "null".equals(evidenceJson) ? ""
                : "\n\n<intent_routing_evidence_untrusted>\n" + evidenceJson
                + "\n</intent_routing_evidence_untrusted>";
    }

    private static String escapeClosingTags(String json) {
        return json == null ? null : json.replace("</", "<\\/");
    }

    private static void validateSupervisor(CapabilityExecutionRequest request, AgentRuntimeSnapshot snapshot,
                                           RuntimeCapabilityInvoker capabilityInvoker) {
        AgentRuntimeDefinition definition = request == null || snapshot == null ? null : snapshot.definition();
        if (request == null || request.getCapability() == null || request.getRun() == null
                || request.getInputs().getString("query") == null
                || request.getInputs().getString("query").isBlank() || capabilityInvoker == null
                || definition == null || definition.getRole() != AgentRole.SUPERVISOR
                || definition.getPlanPolicy() == null || !definition.resolveToolCodes().isEmpty()
                || definition.getKnowledgeSearch() != null || definition.getUserPromptVersionId() != null) {
            throw invalid("Supervisor AgentVersion运行契约不完整");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatusEnum.CONFLICT, message);
    }

    private record PreparedSupervisor(String systemPrompt, List<ChatHistoryMessage> history,
                                      ChatModelRuntimeConfig modelConfig,
                                      List<RuntimeCapability> runtimeCapabilities) {
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
     * @return 已合并 Application 与 Agent 约束的运行请求
     */
    public AgentRunResult run(Long agentId, Long agentVersionId, String query, ApplicationRun run) {
        return run(agentId, agentVersionId, query, run, null);
    }

    public AgentRunResult run(Long agentId, Long agentVersionId, String query, ApplicationRun run,
                              CapabilityInvocationScope invocationScope) {
        AgentRuntimeSnapshot snapshot = agentVersionService.resolveVersion(agentId, agentVersionId);
        return run(snapshot, query, run, invocationScope);
    }

    public AgentRunResult run(AgentRuntimeSnapshot snapshot, String query, ApplicationRun run) {
        return run(snapshot, query, run, null);
    }

    public AgentRunResult run(AgentRuntimeSnapshot snapshot, String query, ApplicationRun run,
                              CapabilityInvocationScope invocationScope) {
        AgentRuntimeDefinition definition = snapshot.definition();
        // ② 通用校验
        validateCommon(definition);
        RuntimeVariables invocationVariables = run.conversation().getRuntimeVariables().forkForQuery(query);
        // ④ 只激活AgentVersion锁定且仍通过安全熔断的SkillVersion
        SkillRuntimeCatalog skillCatalog = skillRuntimeService.load(
                definition, run.conversation().getBudget(), run.request().getExplicitSkillCodes());
        // ⑤ 领域专家按发布快照渲染Prompt；主Agent必须走独立规划链，不能进入普通对话循环
        ModelChatContext chatContext = prepareSpecialist(definition, invocationVariables);
        // ⑥ 只装配领域专家自己的业务工具和知识搜索工具
        List<AgentTool> runtimeTools = this.loadRuntimeTools(snapshot);

        PromptPair prompt = chatContext.getPromptPair();
        Optional<AgentToolRuntimeConfig> toolRuntimeConfig = resolveToolRuntimeConfig(
                run, invocationVariables, snapshot, runtimeTools);
        // 预算边界判定：工具已装配但工具配置为空 ⇒ 进入收尾模式，本次只做最终回答、不再开放工具
        boolean finalizationOnly = !runtimeTools.isEmpty() && toolRuntimeConfig.isEmpty();
        ApplicationExecutionBudget executionBudget = run.budget();
        // 收尾阶段改用收尾专用 Token 预算，其余情况使用常规剩余预算
        int remainingTokenBudget = executionBudget.shouldFinalize()
                ? executionBudget.remainingFinalizationTokenBudget()
                : executionBudget.remainingTokenBudget();
        // 从模型实例配置出发，叠加 Agent 生成策略与剩余预算，得到本次请求的最终模型配置
        ChatModelRuntimeConfig sourceConfig = modelRuntimeConfigService.getChatModelConfig(
                definition.getChatModelInstanceId());
        int responseIdleTimeoutSeconds = Objects.requireNonNull(
                sourceConfig.getTimeoutSeconds(), "模型响应空闲超时不能为空");
        ChatModelRuntimeConfig modelConfig = applyGenerationPolicy(
                sourceConfig, definition.getGenerationPolicy(), run.conversation().getBudget(), remainingTokenBudget,
                responseIdleTimeoutSeconds);
        AgentRunRequest request = this.resolveEffectiveAgentRequest(snapshot, run, invocationVariables,
                invocationScope, prompt, chatContext, modelConfig, skillCatalog,
                resolveAuthorizedToolCodes(definition.resolveToolCodes(), run.request().getPreResolvedDispatch()),
                toolRuntimeConfig, finalizationOnly, remainingTokenBudget);
        com.lingxi.workbench.application.runtime.model.ExecutionResumeContext resume = run.request().getResumeContext();
        return resume == null
                ? agentRuntime.run(request)
                : agentRuntime.resume(new AgentResumeRequest(request, resume.getCheckpoint(),
                resume.getInteractionId(), resume.getFingerprint()));
    }


    /**
     * 解析本次运行实际可用的工具集。
     *
     * <p>默认使用 AgentVersion 发布快照锁定的工具集；定时触发器等预解析分发入口
     * 携带预授权白名单时，与默认工具集求交收紧，只允许白名单内的工具被调用。</p>
     */
    private static List<String> resolveAuthorizedToolCodes(List<String> defaultToolCodes,
                                                           PreResolvedDispatch preResolvedDispatch) {
        if (preResolvedDispatch == null || preResolvedDispatch.allowedToolCodes().isEmpty()) {
            return defaultToolCodes;
        }
        return defaultToolCodes.stream()
                .filter(preResolvedDispatch.allowedToolCodes()::contains)
                .toList();
    }

    /**
     * 构建领域专家的直接模型上下文。
     */
    private ModelChatContext prepareSpecialist(AgentRuntimeDefinition definition,
                                               RuntimeVariables runtimeVariables) {
        if (definition.getRole() != AgentRole.SPECIALIST) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "只有领域专家可以进入ReAct执行链");
        }
        PromptRuntimeSnapshot userPrompt = promptRuntimeService.resolvePublished(definition.getUserPromptVersionId());
        ModelChatContext context = ModelChatContext.builder()
                .runtimeVariables(runtimeVariables)
                .systemPrompt(promptRuntimeService.resolvePublished(definition.getSystemPromptVersionId()))
                .userPrompt(userPrompt)
                .build();
        context.setPromptPair(modelRequestFactory.createDirectPrompt(context));
        return context;
    }

    /**
     * 生成
     */
    private AgentRunRequest resolveEffectiveAgentRequest(AgentRuntimeSnapshot snapshot, ApplicationRun run,
                                                         RuntimeVariables runtimeVariables,
                                                         CapabilityInvocationScope invocationScope, PromptPair prompt,
                                                         ModelChatContext context,
                                                         ChatModelRuntimeConfig modelConfig, SkillRuntimeCatalog skillCatalog,
                                                         List<String> authorizedToolCodes,
                                                         Optional<AgentToolRuntimeConfig> toolRuntimeConfig,
                                                         boolean finalizationOnly, int remainingTokenBudget) {
        ApplicationExecutionBudget executionBudget = run.budget();
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
        RuntimeEventSink eventSink = runtimeEventSinkFactory.create(run.budget(),
                invocationScope == null ? run.request().getEventPublisher() : invocationScope.eventPublisher());
        com.lingxi.workbench.application.runtime.model.ExecutionResumeContext resume = run.request().getResumeContext();
        boolean hasModelRoutedSkill = !finalizationOnly && skillCatalog.bindings().stream()
                .anyMatch(binding -> binding.getSkill().getActivationMode()
                        == com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode.MODEL_ROUTED);
        int maxModelCalls = resume == null
                ? toolRuntimeConfig.isPresent() ? executionBudget.remainingModelCalls()
                : hasModelRoutedSkill ? Math.min(executionBudget.remainingModelCalls(), 2) : 1
                : resume.getCheckpoint().getMaxModelCalls();
        int maxToolCalls = resume == null
                ? toolRuntimeConfig.isPresent() ? executionBudget.remainingToolCalls() : 0
                : resume.getCheckpoint().getMaxToolCalls();
        ExecutionPolicy policy = resume == null
                ? ExecutionPolicy.builder()
                .maxModelCalls(maxModelCalls)
                .maxToolCalls(maxToolCalls)
                .maxTotalTokens(remainingTokenBudget)
                .maxFinalizationTokens(executionBudget.remainingFinalizationTokenBudget())
                .build()
                : ExecutionPolicy.builder()
                .maxModelCalls(maxModelCalls)
                .maxToolCalls(maxToolCalls)
                .maxTotalTokens(resume.getCheckpoint().getMaxTotalTokens())
                .maxFinalizationTokens(resume.getCheckpoint().getMaxFinalizationTokens())
                .build();
        boolean approvalPossible = toolRuntimeConfig.stream()
                .flatMap(config -> config.getTools().stream())
                .anyMatch(tool -> tool.definition().getSecurityPolicy().isApprovalRequired());
        AgentScopeCheckpoint checkpointSeed = resume != null
                ? resume.getCheckpoint()
                : approvalPossible ? AgentScopeCheckpoint.builder()
                .query(runtimeVariables.require(RuntimeVariableAccessors.USER_QUERY))
                .capabilityCode(resolveCapabilityCode(run, snapshot))
                .applicationVersionId(run.request().getApplication().versionId())
                .userId(runtimeVariables.require(RuntimeVariableAccessors.USER_ID))
                .conversationId(run.request().getTurn().conversationId())
                .assistantMessageId(run.request().getTurn().assistantMessageId())
                .explicitSkillCodes(run.request().getExplicitSkillCodes())
                .applicationUsageBeforeAgent(executionBudget.tokenUsage())
                .applicationModelCallsBeforeAgent(executionBudget.usedModelCalls())
                .applicationToolCallsBeforeAgent(executionBudget.usedToolCalls())
                .agentSessionId(invocationScope == null
                        ? run.agentSessionId() : invocationScope.agentSessionId())
                .build()
                : null;
        return AgentRunRequest.builder()
                .runId(run.executionId())
                .agentId(snapshot.agentId())
                .agentVersionId(snapshot.agentVersionId())
                .role(AgentRole.SPECIALIST)
                .scope(invocationScope == null ? com.lingxi.workbench.agentruntime.model.ExecutionScope.RUN
                        : invocationScope.executionScope())
                .modelCommand(command)
                .userId(String.valueOf(runtimeVariables.require(RuntimeVariableAccessors.USER_ID)))
                .sessionId(invocationScope == null ? run.agentSessionId() : invocationScope.agentSessionId())
                .skillCatalog(skillCatalog)
                .authorizedToolCodes(authorizedToolCodes)
                .finalizationOnly(finalizationOnly)
                .policy(policy)
                .eventSink(eventSink)
                .initialSequenceNo(executionPersistenceService.lastEventSequence(run.executionId()))
                .initialModelCalls(0)
                .initialUsage(TokenUsage.empty())
                .checkpointSeed(checkpointSeed)
                .checkpointStore(checkpointStore)
                .build();
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
    private Optional<AgentToolRuntimeConfig> resolveToolRuntimeConfig(ApplicationRun run,
                                                                      RuntimeVariables runtimeVariables,
                                                                      AgentRuntimeSnapshot snapshot,
                                                                      List<AgentTool> runtimeTools) {
        AgentRuntimeDefinition definition = snapshot.definition();
        if (runtimeTools.isEmpty()) {
            return Optional.empty();
        }
        ApplicationExecutionBudget executionBudget = run.budget();
        // 预算边界：已进入收尾阶段或剩余工具调用次数不足时，本次调用降级为纯文本回答
        int generationModelCalls = executionBudget.remainingModelCalls();
        int maxToolCalls = executionBudget.remainingToolCalls();
        if (run.request().getResumeContext() == null
                && (executionBudget.shouldFinalize() || maxToolCalls < MIN_TOOL_CALLS)) {
            return Optional.empty();
        }
        int maxResultCharacters = Optional.ofNullable(definition.getToolPolicy())
                .map(AgentRuntimeDefinition.ToolPolicy::maxResultCharacters)
                .orElse(DEFAULT_DELEGATION_RESULT_CHARACTERS);
        AgentToolExecutionContext toolExecutionContext = AgentToolExecutionContext.builder()
                .executionId(run.executionId())
                .userId(runtimeVariables.require(RuntimeVariableAccessors.USER_ID))
                .conversationId(run.request().getTurn().conversationId())
                .assistantMessageId(run.request().getTurn().assistantMessageId())
                .build();

        AgentToolRuntimeConfig runtimeConfig = AgentToolRuntimeConfig.builder()
                .tools(runtimeTools)
                .executionContext(toolExecutionContext)
                .maxResultCharacters(maxResultCharacters)
                .build();
        return Optional.of(runtimeConfig);
    }

    private static String resolveCapabilityCode(ApplicationRun run, AgentRuntimeSnapshot agent) {
        return run.request().getApplication().definition().capabilityCatalog()
                .capabilities().stream()
                .filter(capability -> capability.getType() == ApplicationCapabilityType.AGENT)
                .filter(capability -> Objects.equals(capability.getTargetId(), agent.agentId()))
                .filter(capability -> Objects.equals(capability.getTargetVersionId(), agent.agentVersionId()))
                .map(CapabilityDefinition::getCode)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.CONFLICT, "当前Agent不在Application能力目录中"));
    }

    /**
     * 在模型实例基础配置上叠加 Agent 生成策略与上下文预算，得到本次请求的最终模型配置。
     *
     * <p>采样参数（temperature/topP）优先采用发布版本中显式配置的值；maxTokens 取模型配置、
     * 上下文预留输出 Token 与剩余 Token 预算三者的较小值，保证单次输出不会挤爆共享预算。
     * 模型实例的响应空闲超时原样保留，不受 Application 运行时长影响。</p>
     *
     * @param source                     模型实例的原始配置
     * @param policy                     Agent 发布版本声明的生成策略，可为 null
     * @param contextBudget              上下文预算（预留输出 Token）
     * @param remainingTokenBudget       当前剩余 Token 预算
     * @param responseIdleTimeoutSeconds 模型实例发布的响应空闲超时秒数
     * @return 叠加策略与预算后的最终模型配置
     */
    private static ChatModelRuntimeConfig applyGenerationPolicy(ChatModelRuntimeConfig source,
                                                                AgentRuntimeDefinition.GenerationPolicy policy,
                                                                ContextBudgetPolicy contextBudget,
                                                                int remainingTokenBudget,
                                                                int responseIdleTimeoutSeconds) {
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

        return source.toBuilder()
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxTokens)
                .timeoutSeconds(responseIdleTimeoutSeconds)
                .build();
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

package com.lingxi.workbench.application.runtime.service.plan;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.agentruntime.planner.PlannerClarification;
import com.lingxi.workbench.agentruntime.planner.PlannerDecision;
import com.lingxi.workbench.agentruntime.planner.PlannerDecisionAction;
import com.lingxi.workbench.agentruntime.planner.PlannerInteractionType;
import com.lingxi.workbench.agentruntime.planner.PlannerRunResult;
import com.lingxi.workbench.agentruntime.planner.PlannerStep;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.ExecutionEventCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionFailure;
import com.lingxi.workbench.application.runtime.model.ExecutionPlanSnapshotCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionStepAttemptCommand;
import com.lingxi.workbench.application.runtime.model.FinalSynthesisRequest;
import com.lingxi.workbench.application.runtime.model.FinalSynthesisResult;
import com.lingxi.workbench.application.runtime.model.InteractionSessionContext;
import com.lingxi.workbench.application.runtime.model.InteractionSessionOpenCommand;
import com.lingxi.workbench.application.runtime.model.PlanExecutionRequest;
import com.lingxi.workbench.application.runtime.model.PlanExecutionResult;
import com.lingxi.workbench.application.runtime.model.PlanStepResult;
import com.lingxi.workbench.application.runtime.model.PlannerRequest;
import com.lingxi.workbench.application.runtime.service.capability.CapabilityExecutionService;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionPersistenceService;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionFailureClassifier;
import com.lingxi.workbench.application.runtime.service.plan.FinalSynthesisService;
import com.lingxi.workbench.application.runtime.service.interaction.InteractionSessionService;
import com.lingxi.workbench.application.runtime.service.plan.PlanExecutionService;
import com.lingxi.workbench.application.runtime.service.plan.PlannerService;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.chat.model.bo.ChatPlanStepBO;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.CapabilityRiskLevel;
import com.lingxi.workbench.infra.ai.agent.model.PlanPolicy;
import com.lingxi.workbench.workflow.model.WorkflowNodeTrace;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

/**
 * 计划执行引擎：串行执行计划步骤、解析步骤依赖输入、失败分类与有限重规划、风险能力门禁，并落库步骤事件。
 * <p>主循环按计划编号推进：每轮先做活性与预算边界检查并落库计划快照；
 * 决策为澄清时建立可恢复的用户澄清会话，为直接能力调用时走单能力直执行路径，
 * 否则串行执行计划步骤；步骤失败按可重试性分类，可重试且未耗尽重规划额度时
 * 携带失败观察重新调用 Planner，否则基于已有完整步骤结果做无Tool最终综合；
 * 全过程经 Micrometer Observation 观测，时间线事件统一落库并发布到事件总线。</p>
 */
@Service
@RequiredArgsConstructor
public class PlanExecutionServiceImpl implements PlanExecutionService {

    private final CapabilityExecutionService capabilityExecutionService;
    private final PlannerService plannerService;
    private final FinalSynthesisService finalSynthesisService;
    private final ExecutionPersistenceService persistenceService;
    private final InteractionSessionService interactionSessionService;
    private final PlanInputCompiler inputCompiler;
    private final ExecutionFailureClassifier failureClassifier;
    private final ObservationRegistry observationRegistry;

    /**
     * 执行一次 Plan-and-Execute 运行（对外入口）。
     * <p>先校验请求完整性，再创建 Micrometer Observation 包裹整个执行过程，
     * 便于链路追踪聚合计划与执行的耗时与状态；取消与运行时异常统一由
     * {@link #executeObserved} 兜底发布终态事件。</p>
     *
     * @param request 计划执行请求（含运行 ID、初始决策、执行上下文等）
     * @return 计划执行结果（最终内容、模型实例 ID 与节点轨迹）
     */
    @Override
    public PlanExecutionResult execute(PlanExecutionRequest request) {
        requireRequest(request);
        // 创建执行级观测：携带运行 ID 高基数标签，包裹整个执行过程便于链路追踪
        Observation observation = Observation.createNotStarted("lingxi.ai.plan", observationRegistry)
                .contextualName("plan-and-execute")
                .highCardinalityKeyValue("lingxi.run.id", request.getRunId());
        return observation.observe(() -> executeObserved(request));
    }

    /**
     * Observation 观测回调：执行计划并统一兜底终态失败事件。
     * <p>取消（用户中断/线程中断）发布 RUN_CANCELLED 后原样抛出；
     * 其余运行时异常先经失败分类器分类，发布 RUN_FAILED 后继续抛出，保证调用方可见原始异常。</p>
     *
     * @param request 计划执行请求
     * @return 计划执行结果
     */
    private PlanExecutionResult executeObserved(PlanExecutionRequest request) {
        try {
            return executePlan(request);
        // 终态兜底：取消发布 RUN_CANCELLED，其余异常分类后发布 RUN_FAILED，均原样抛出
        } catch (CancellationException exception) {
            publishTerminalFailure(request, ExecutionEventType.RUN_CANCELLED, "计划执行已取消", exception);
            throw exception;
        } catch (RuntimeException exception) {
            ExecutionFailure failure = failureClassifier.classify(exception);
            publishTerminalFailure(request, ExecutionEventType.RUN_FAILED, failure.clientMessage(), exception);
            throw exception;
        }
    }

    /**
     * 计划执行主循环：决策驱动，串行执行步骤并在失败时有限重规划。
     * <p>每轮循环先校验运行活性并落库计划快照；CLARIFY 决策直接转澄清，
     * 非 EXECUTE_PLAN 决策走单能力直执行，否则发布 PLAN_CREATED 后逐步骤串行执行。
     * 步骤失败按 {@link ExecutionFailure#retryable()} 分类：不可重试直接终止；
     * 可重试但重规划次数耗尽或预算触及软边界时，有部分结果则进入最终综合、无结果则澄清；
     * 否则递增计划编号携带失败观察重新调用 Planner 决策。</p>
     *
     * @param request 计划执行请求
     * @return 计划执行结果
     */
    private PlanExecutionResult executePlan(PlanExecutionRequest request) {
        PlanPolicy policy = request.getSupervisor().getAgent().definition().getPlanPolicy();
        Map<String, ApplicationCapabilityDefinition> capabilities = capabilityIndex(request);
        PlannerDecision decision = request.getInitialDecision();
        int planNo = 1;
        int replans = 0;
        Map<String, Integer> capabilityCalls = new HashMap<>();
        Set<String> completedWriteCapabilities = new HashSet<>();
        List<PlanStepResult> completedResults = new ArrayList<>();
        List<WorkflowNodeTrace> traces = new ArrayList<>();
        Long modelInstanceId = null;

        // 主循环：每轮先做活性检查（取消/中断/截止时间）并落库计划快照，再按决策类型分支推进
        while (true) {
            ensureActive(request);
            persistenceService.appendPlan(new ExecutionPlanSnapshotCommand(request.getRunId(), planNo, decision));
            // 决策要求澄清：不执行任何能力，直接返回澄清问题等待用户补充信息
            if (decision.getAction() == PlannerDecisionAction.CLARIFY) {
                return clarify(request, decision, planNo);
            }
            // 硬边界门禁：预算已尽时不再发起任何新的能力调用，转澄清避免执行被中途掐断
            if (!request.getExecutionContext().hasFinalizationCapacity()) {
                return clarifyAtBoundary(request, planNo,
                        "Application运行预算已到硬边界，未继续发起新的能力调用");
            }
            // 非计划决策（单能力直执行）：跳过步骤循环，直接调用决策引用的能力
            if (decision.getAction() != PlannerDecisionAction.EXECUTE_PLAN) {
                CapabilityExecutionResult result = executeDirect(request, decision, planNo, capabilities);
                publish(request, event(request, ExecutionEventType.RUN_COMPLETED).planNo(planNo).build(), List.of());
                return new PlanExecutionResult(result.content(), result.modelInstanceId(), result.traces());
            }
            // Planner Token 软边界：宽限额度仅保留给最终回答，不再开启新一轮计划执行
            if (completedResults.isEmpty() && request.getExecutionContext().hasReachedTokenSoftLimit()) {
                return clarifyAtBoundary(request, planNo,
                        "Planner已用尽普通工作Token，宽限额度仅保留给最终回答");
            }
            try {
                // 串行执行计划内每个步骤：依赖前序已完成结果，逐步骤推进
                for (PlannerStep step : decision.getPlan().steps()) {
                    ensureActive(request);
                    // 软边界：已有部分结果且预算即将耗尽时，停止剩余步骤提前进入最终综合
                    if (!completedResults.isEmpty() && request.getExecutionContext().shouldFinalize()) {
                        return finalizePlan(request, completedResults, modelInstanceId, traces, planNo,
                                "Application运行预算已到软边界，未继续执行剩余步骤");
                    }
                    ApplicationCapabilityDefinition capability = requireCapability(step.getCapabilityCode(), capabilities);
                    // 能力调用次数门禁：超过能力定义的最大调用次数直接判步骤失败
                    int nextCalls = capabilityCalls.getOrDefault(capability.getCode(), 0) + 1;
                    if (nextCalls > capability.getMaxCalls()) {
                        throw new StepFailure(step, "CAPABILITY_CALL_LIMIT");
                    }
                    // 副作用门禁：写类能力在同一运行中只允许成功执行一次，避免重复产生副作用
                    if (capability.getRiskLevel() != CapabilityRiskLevel.READ_ONLY
                            && completedWriteCapabilities.contains(capability.getCode())) {
                        throw new StepFailure(step, "SIDE_EFFECT_ALREADY_COMPLETED");
                    }
                    capabilityCalls.put(capability.getCode(), nextCalls);
                    CapabilityExecutionResult result = executeStep(
                            request, planNo, step, capability, completedResults, true);
                    // 记录步骤结果与轨迹，供后续步骤依赖解析与最终综合使用；模型实例 ID 取最近一次
                    completedResults.add(PlanStepResult.builder()
                            .stepId(step.getId())
                            .name(step.getName())
                            .capabilityCode(step.getCapabilityCode())
                            .content(result.content())
                            .build());
                    traces.addAll(result.traces());
                    if (result.modelInstanceId() != null) {
                        modelInstanceId = result.modelInstanceId();
                    }
                    // 写类能力执行成功后登记，后续步骤再引用同能力即被副作用门禁拦截
                    if (capability.getRiskLevel() != CapabilityRiskLevel.READ_ONLY) {
                        completedWriteCapabilities.add(capability.getCode());
                    }
                }
                return finalizePlan(request, completedResults, modelInstanceId, traces, planNo, null);
            // 步骤失败：不可重试（门禁失败、非重试错误等）直接终止计划执行
            } catch (StepFailure failure) {
                if (!failure.failure.retryable()) {
                    throw invalid("计划步骤执行失败：" + failure.failure.clientMessage());
                }
                // 重规划不可用（重规划次数耗尽或预算到软边界）：有部分结果则基于现有结果收尾，否则澄清
                boolean replanUnavailable = replans >= policy.getMaxReplans()
                        || request.getExecutionContext().shouldFinalize();
                if (replanUnavailable) {
                    String limitation = "计划步骤未完成，且无法继续重规划：" + failure.failure.clientMessage();
                    if (!completedResults.isEmpty()) {
                        return finalizePlan(request, completedResults, modelInstanceId, traces, planNo, limitation);
                    }
                    return clarifyAtBoundary(request, planNo, limitation);
                }
                // 进入新一轮规划：递增重规划计数与计划编号，携带失败观察重新调用 Planner 决策
                replans++;
                planNo++;
                PlannerRunResult replanned = plannerService.decide(PlannerRequest.builder()
                        .conversationContext(request.getConversationContext())
                        .executionContext(request.getExecutionContext())
                        .planNo(planNo)
                        .failureObservation(failure.observation())
                        .confirmedCapabilityCodes(request.confirmedCapabilityCodes())
                        .supervisor(request.getSupervisor())
                        .build());
                request.getExecutionContext().recordUsage(replanned.tokenUsage());
                decision = replanned.decision();
            }
        }
    }

    /**
     * 计划正常完成或触及软边界后统一进入无Tool最终综合。
     * <p>先发布 FINALIZING 事件；若预算已到硬边界（无最终综合容量），
     * 直接返回最后一个完整步骤结果并结束运行，避免超预算执行；
     * 否则调用综合服务基于已完成步骤结果生成最终回答，并记录其 Token 用量。</p>
     *
     * @param request          计划执行请求
     * @param completedResults 已完成步骤的结果列表（作为最终综合输入）
     * @param modelInstanceId  已记录的模型实例 ID，原样透传给最终结果
     * @param traces           已累积的工作流节点轨迹，原样透传给最终结果
     * @param planNo           当前计划编号
     * @param limitation       软边界原因描述，为 null 表示正常完成
     * @return 计划执行结果
     */
    private PlanExecutionResult finalizePlan(PlanExecutionRequest request, List<PlanStepResult> completedResults,
                                             Long modelInstanceId, List<WorkflowNodeTrace> traces, int planNo,
                                             String limitation) {
        boolean limited = Objects.nonNull(limitation) && !limitation.isBlank();
        String progressMessage = limited ? "已停止继续探索，正在基于现有结果整理回答" : "正在整理最终回答";
        publish(request, event(request, ExecutionEventType.FINALIZING)
                .planNo(planNo).message(progressMessage).build(), List.of());
        // 硬边界兜底：无最终综合容量时返回最后一个完整步骤结果，不丢弃已产出的内容
        if (!request.getExecutionContext().hasFinalizationCapacity()) {
            PlanStepResult lastResult = completedResults.getLast();
            publish(request, event(request, ExecutionEventType.RUN_COMPLETED)
                    .planNo(planNo).message("运行预算已到硬边界，已返回最后一个完整步骤结果").build(), List.of());
            return new PlanExecutionResult(lastResult.getContent(), modelInstanceId, traces);
        }
        // 无Tool最终综合：基于已完成步骤结果生成最终回答，并记录综合消耗的 Token
        FinalSynthesisResult synthesis = finalSynthesisService.synthesize(FinalSynthesisRequest.builder()
                .conversationContext(request.getConversationContext())
                .executionContext(request.getExecutionContext())
                .stepResults(completedResults)
                .limitation(limitation)
                .supervisor(request.getSupervisor())
                .build());
        request.getExecutionContext().recordUsage(synthesis.tokenUsage());
        String completedMessage = limited ? "已基于现有信息完成回答" : null;
        publish(request, event(request, ExecutionEventType.RUN_COMPLETED)
                .planNo(planNo).message(completedMessage).build(), List.of());
        return new PlanExecutionResult(synthesis.content(), modelInstanceId, traces);
    }

    /**
     * 没有任何完整步骤结果时不伪造答案，改为建立可恢复的用户澄清会话。
     * <p>构造固定文案的 CLARIFY 决策（CLARIFICATION 类型），
     * 先发布 FINALIZING 事件说明边界原因，再走统一澄清流程等待用户补充信息。</p>
     *
     * @param request    计划执行请求
     * @param planNo     当前计划编号
     * @param limitation 触发处理边界的限制说明
     * @return 计划执行结果（内容为澄清问题）
     */
    private PlanExecutionResult clarifyAtBoundary(PlanExecutionRequest request, int planNo, String limitation) {
        String question = "本轮执行已达到处理边界，现有信息不足以形成可靠答案。请补充最关键的目标、材料或限制条件后继续。";
        PlannerDecision clarification = PlannerDecision.builder()
                .action(PlannerDecisionAction.CLARIFY)
                .clarification(PlannerClarification.builder()
                        .question(question)
                        .options(List.of())
                        .interactionType(PlannerInteractionType.CLARIFICATION)
                        .capabilityCodes(List.of())
                        .build())
                .build();
        publish(request, event(request, ExecutionEventType.FINALIZING)
                .planNo(planNo).message(limitation).build(), List.of());
        return clarify(request, clarification, planNo);
    }

    /**
     * 兜底发布运行终态失败事件（取消/异常）。
     * <p>事件发布属尽力而为：持久化或发布失败时把失败异常附加到原始异常上
     * （addSuppressed），绝不掩盖导致运行终止的原始异常。</p>
     *
     * @param request   计划执行请求
     * @param eventType 终态事件类型（RUN_CANCELLED / RUN_FAILED）
     * @param message   失败消息
     * @param original  原始异常，用于携带发布失败的抑制异常
     */
    private void publishTerminalFailure(PlanExecutionRequest request, ExecutionEventType eventType, String message, RuntimeException original) {
        try {
            publish(request, event(request, eventType).message(message).build(), List.of());
        // 终态事件发布失败不掩盖原始异常：附加为抑制异常后继续抛出
        } catch (RuntimeException persistenceFailure) {
            original.addSuppressed(persistenceFailure);
        }
    }

    /**
     * 将澄清决策落地为可恢复的用户交互会话并发布事件。
     * <p>沿用 Planner 给出的选项，通过统一交互会话等待用户补充信息；
     * 通过 {@link InteractionSessionService#open} 建立可恢复会话（记录应用快照、用户、运行、
     * 恢复能力与编排 Agent 信息），再发布 CLARIFICATION_REQUIRED 事件。</p>
     *
     * @param request  计划执行请求
     * @param decision 澄清决策（含澄清问题、选项与关联能力）
     * @param planNo   当前计划编号
     * @return 计划执行结果（内容为澄清问题，无模型实例与轨迹）
     */
    private PlanExecutionResult clarify(PlanExecutionRequest request, PlannerDecision decision, int planNo) {
        PlannerClarification interactionClarification = PlannerClarification.builder()
                .question(decision.getClarification().getQuestion())
                .options(decision.getClarification().options())
                .interactionType(decision.getClarification().getInteractionType())
                .capabilityCodes(decision.getClarification().capabilityCodes())
                .build();
        InteractionSessionContext sessionContext = InteractionSessionContext.builder()
                .clarification(interactionClarification)
                .capabilityCodes(decision.getClarification().capabilityCodes())
                .originalQuery(request.getConversationContext().getOriginalQuery())
                .build();
        request.getExecutionContext().getEventPublisher().publishClarification(
                interactionSessionService.open(InteractionSessionOpenCommand.builder()
                        .application(request.getConversationContext().getApplicationSnapshot())
                        .turn(request.getExecutionContext().getTurn())
                        .userId(request.getConversationContext().getLoginUser().id())
                        .runId(request.getRunId())
                        .resumeCapabilityCode(request.getSupervisor().getCapability().getCode())
                        .orchestratorAgentId(request.getSupervisor().getAgent().agentId())
                        .orchestratorAgentVersionId(request.getSupervisor().getAgent().agentVersionId())
                        .interactionType(InteractionSessionType.CLARIFICATION)
                        .context(sessionContext)
                        .build()));
        publish(request, event(request, ExecutionEventType.CLARIFICATION_REQUIRED)
                .planNo(planNo).message(decision.getClarification().getQuestion()).build(), List.of());
        return new PlanExecutionResult(decision.getClarification().getQuestion(), null, List.of());
    }

    /**
     * 单能力直执行：决策不需要计划（如直接调用某能力）时的快捷路径。
     * <p>将决策引用的能力包装为 id 为 "direct" 的虚拟步骤（无依赖），
     * 复用步骤执行链路，且不发布步骤输出事件。</p>
     *
     * @param request      计划执行请求
     * @param decision     非 EXECUTE_PLAN 的执行决策（携带目标能力编码）
     * @param planNo       当前计划编号
     * @param capabilities 应用能力索引（编码 → 定义）
     * @return 能力执行结果
     */
    private CapabilityExecutionResult executeDirect(PlanExecutionRequest request, PlannerDecision decision, int planNo,
                                                    Map<String, ApplicationCapabilityDefinition> capabilities) {
        ApplicationCapabilityDefinition capability = requireCapability(decision.getCapabilityCode(), capabilities);
        PlannerStep step = PlannerStep.builder()
                .id("direct")
                .name(capability.getName())
                .capabilityCode(capability.getCode())
                .dependsOn(List.of())
                .build();
        return executeStep(request, planNo, step, capability, List.of(), false);
    }

    /**
     * 执行单个计划步骤（带观测埋点）。
     * <p>为每个步骤创建独立 Observation，携带运行、计划、步骤、能力等标签，
     * 便于按步骤粒度追踪与统计；实际执行委托 {@link #executeObservedStep}。</p>
     *
     * @param request           计划执行请求
     * @param planNo            当前计划编号
     * @param step              待执行步骤
     * @param capability        步骤对应的能力定义
     * @param completedResults  已完成步骤结果（作为依赖输入来源）
     * @param publishStepOutput 是否发布步骤输出事件（计划内步骤为 true，直执行为 false）
     * @return 能力执行结果
     */
    private CapabilityExecutionResult executeStep(PlanExecutionRequest request, int planNo, PlannerStep step,
                                                  ApplicationCapabilityDefinition capability,
                                                  List<PlanStepResult> completedResults, boolean publishStepOutput) {
        Observation observation = Observation.createNotStarted("lingxi.ai.plan.step", observationRegistry)
                .contextualName("plan-step")
                .lowCardinalityKeyValue("lingxi.capability.type", capability.getType().name())
                .highCardinalityKeyValue("lingxi.run.id", request.getRunId())
                .highCardinalityKeyValue("lingxi.plan.no", String.valueOf(planNo))
                .highCardinalityKeyValue("lingxi.step.id", step.getId())
                .highCardinalityKeyValue("lingxi.capability.code", capability.getCode());
        return observation.observe(() -> executeObservedStep(
                request, planNo, step, capability, completedResults, publishStepOutput));
    }

    /**
     * 步骤执行的观测回调：解析依赖输入、执行能力、落库步骤尝试并发布状态事件。
     * <p>正常路径：编译入参（含依赖步骤结果解析）→ 解析步骤上下文 → 执行能力 →
     * 落库 COMPLETED 尝试并发布 STEP_COMPLETED。
     * 异常路径：取消落库 CANCELLED 后原样抛出；运行时异常经分类器分类后落库 FAILED、
     * 发布 STEP_FAILED，并包装为 {@link StepFailure} 供主循环做重规划决策。</p>
     *
     * @param request           计划执行请求
     * @param planNo            当前计划编号
     * @param step              待执行步骤
     * @param capability        步骤对应的能力定义
     * @param completedResults  已完成步骤结果（依赖输入来源）
     * @param publishStepOutput 是否发布步骤输出事件
     * @return 能力执行结果
     */
    private CapabilityExecutionResult executeObservedStep(PlanExecutionRequest request, int planNo, PlannerStep step,
                                                           ApplicationCapabilityDefinition capability,
                                                           List<PlanStepResult> completedResults,
                                                           boolean publishStepOutput) {
        // 步骤开始：记录开始时间并发布 RUNNING 状态事件，前端据此展示步骤进行中
        OffsetDateTime startedAt = OffsetDateTime.now();
        publish(request, event(request, ExecutionEventType.STEP_STARTED)
                .planNo(planNo).stepId(step.getId()).displayName(step.getName())
                .stepStatus(ExecutionStepStatus.RUNNING).build(), List.of());
        try {
            // 每次计划调用领域专家都占用统一委派额度，重规划和重试同样计数。
            request.getExecutionContext().reserveDelegation();
            // 编译步骤入参：解析命名入参与依赖步骤结果，得到能力实际执行的输入
            JSONObject inputs = inputCompiler.compile(
                    step, request.getConversationContext().routingQuery(), completedResults);
            ChatExecutionContext stepContext = resolveStepContext(
                    request, planNo, step, inputs, publishStepOutput);
            CapabilityExecutionResult result = capabilityExecutionService.execute(
                    CapabilityExecutionRequest.builder()
                            .capability(capability)
                            .inputs(inputs)
                            .context(stepContext)
                            .confirmedCapabilityCodes(request.confirmedCapabilityCodes())
                            .build());
            // 步骤成功：落库 COMPLETED 尝试记录并发布成功事件
            persistenceService.appendStepAttempt(attempt(request, planNo, step,
                    new StepAttemptOutcome(ExecutionStepStatus.COMPLETED, result.content(), null), startedAt));
            publish(request, event(request, ExecutionEventType.STEP_COMPLETED)
                    .planNo(planNo).stepId(step.getId()).displayName(step.getName())
                    .stepStatus(ExecutionStepStatus.COMPLETED).build(), List.of());
            return result;
        // 取消：落库 CANCELLED 尝试后原样抛出，交由外层发布运行取消事件
        } catch (CancellationException exception) {
            persistenceService.appendStepAttempt(attempt(request, planNo, step,
                    new StepAttemptOutcome(ExecutionStepStatus.CANCELLED, null, "CANCELLED"), startedAt));
            throw exception;
        // 运行时异常：分类器判定可重试性，落库 FAILED 并发布失败事件，包装为 StepFailure 供主循环决策
        } catch (RuntimeException exception) {
            ExecutionFailure failure = failureClassifier.classify(exception);
            persistenceService.appendStepAttempt(attempt(request, planNo, step,
                    new StepAttemptOutcome(ExecutionStepStatus.FAILED, null, failure.code()), startedAt));
            publish(request, event(request, ExecutionEventType.STEP_FAILED)
                    .planNo(planNo).stepId(step.getId()).displayName(step.getName())
                    .stepStatus(ExecutionStepStatus.FAILED).message(failure.clientMessage()).build(), List.of());
            throw new StepFailure(step, failure, exception);
        }
    }

    /**
     * 解析领域专家步骤的执行上下文。
     * <p>编译入参必须包含非空 "query"。计划内步骤创建独立发布作用域，直接执行则只创建
     * 问题作用域，避免中间步骤的模型上下文和文本污染最终回答。</p>
     *
     * @param request           计划执行请求
     * @param planNo            当前计划编号
     * @param step              待执行步骤
     * @param inputs            编译后的步骤入参
     * @param publishStepOutput 是否发布步骤输出事件
     * @return 步骤执行上下文
     */
    private static ChatExecutionContext resolveStepContext(PlanExecutionRequest request, int planNo, PlannerStep step,
                                                           JSONObject inputs, boolean publishStepOutput) {
        ChatExecutionContext context = request.getExecutionContext();
        String query = inputs.getString("query");
        if (query == null || query.isBlank()) {
            throw invalid("计划步骤执行问题不能为空");
        }
        // 计划内步骤 fork 独立作用域，避免各步骤模型上下文互相污染；直执行复用普通查询作用域
        if (publishStepOutput) {
            return context.forkForPlanStep(query, request.getRunId(), planNo, step.getId());
        }
        return context.forkForQuery(query);
    }

    /**
     * 构造步骤尝试落库命令。
     * <p>当前实现每次执行固定为第 1 次尝试（attemptNo=1）：步骤本身不重试，
     * 失败由主循环通过重规划处理；记录开始时间用于耗时统计。</p>
     *
     * @param request   计划执行请求
     * @param planNo    当前计划编号
     * @param step      待记录步骤
     * @param outcome   步骤终态（成功结果或失败编码）
     * @param startedAt 步骤开始时间
     * @return 步骤尝试落库命令
     */
    private static ExecutionStepAttemptCommand attempt(PlanExecutionRequest request, int planNo, PlannerStep step,
                                                       StepAttemptOutcome outcome, OffsetDateTime startedAt) {
        return ExecutionStepAttemptCommand.builder()
                .runId(request.getRunId())
                .planNo(planNo)
                .attemptNo(1) // 当前无步骤级重试，失败统一由主循环重规划处理
                .step(step)
                .status(outcome.status())
                .resultContent(outcome.result())
                .errorCode(outcome.errorCode())
                .startedAt(startedAt)
                .build();
    }

    /**
     * 发布执行事件：双通道写（持久化 + 事件总线）。
     * <p>先将时间线事件（含关联步骤列表）落库供审计与回放，
     * 再发布到执行上下文的事件总线供前端时间线实时渲染，两步基于同一时间线对象保证内容一致。</p>
     *
     * @param request  计划执行请求
     * @param timeline 执行时间线事件
     * @param steps    该事件关联的步骤列表（如计划步骤快照）
     */
    private void publish(PlanExecutionRequest request, ChatExecutionTimelineEventBO timeline,
                         List<ChatPlanStepBO> steps) {
        // 双通道发布：先落库（供审计/回放），再推送到事件总线（供前端实时渲染）
        persistenceService.appendEvent(ExecutionEventCommand.builder()
                .runId(request.getRunId())
                .eventType(timeline.getEventType())
                .planNo(timeline.getPlanNo())
                .stepId(timeline.getStepId())
                .displayName(timeline.getDisplayName())
                .stepStatus(timeline.getStepStatus())
                .message(timeline.getMessage())
                .build());
        ChatExecutionTimelineEventBO published = ChatExecutionTimelineEventBO.builder()
                .runId(timeline.getRunId())
                .eventType(timeline.getEventType())
                .planNo(timeline.getPlanNo())
                .stepId(timeline.getStepId())
                .displayName(timeline.getDisplayName())
                .stepStatus(timeline.getStepStatus())
                .message(timeline.getMessage())
                .steps(steps)
                .build();
        request.getExecutionContext().getEventPublisher().publishExecution(published);
    }

    /**
     * 构造时间线事件构建器，统一填充运行 ID 与事件类型。
     *
     * @param request 计划执行请求
     * @param type    事件类型
     * @return 时间线事件构建器
     */
    private static ChatExecutionTimelineEventBO.ChatExecutionTimelineEventBOBuilder event(
            PlanExecutionRequest request, ExecutionEventType type) {
        return ChatExecutionTimelineEventBO.builder().runId(request.getRunId()).eventType(type);
    }

    /**
     * 构建应用能力索引（编码 → 定义），供步骤与决策按编码解析能力。
     * <p>过滤掉编排器自身的能力编码，防止计划步骤回调编排器自身形成自调用循环。</p>
     *
     * @param request 计划执行请求
     * @return 能力编码到定义的映射
     */
    private static Map<String, ApplicationCapabilityDefinition> capabilityIndex(PlanExecutionRequest request) {
        return request.getConversationContext().getApplicationSnapshot().definition().capabilityCatalog()
                .capabilities().stream()
                // 排除编排器自身能力，防止计划步骤自调用编排器形成循环
                .filter(capability -> !Objects.equals(
                        capability.getCode(), request.getSupervisor().getCapability().getCode()))
                .collect(Collectors.toMap(ApplicationCapabilityDefinition::getCode, capability -> capability));
    }

    /**
     * 按编码查找能力定义，缺失（如引用未发布能力）时抛出业务异常。
     *
     * @param code         能力编码
     * @param capabilities 能力索引
     * @return 能力定义
     */
    private static ApplicationCapabilityDefinition requireCapability(
            String code, Map<String, ApplicationCapabilityDefinition> capabilities) {
        ApplicationCapabilityDefinition capability = capabilities.get(code);
        if (capability == null) {
            throw invalid("执行决策引用了未发布能力：" + code);
        }
        return capability;
    }

    /**
     * 运行活性检查：取消、线程中断或超过截止时间时终止执行。
     * <p>在主循环每轮与每个步骤执行前调用，保证取消请求能被及时响应。</p>
     *
     * @param request 计划执行请求
     */
    private static void ensureActive(PlanExecutionRequest request) {
        // 取消或线程中断立即终止；未取消则校验运行截止时间
        if (request.getExecutionContext().isCancelled() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Plan-and-Execute已取消");
        }
        request.getExecutionContext().ensureWithinDeadline();
    }

    /**
     * 校验执行请求完整性：运行 ID、初始决策、上下文与执行器任一缺失即拒绝。
     *
     * @param request 计划执行请求
     */
    private static void requireRequest(PlanExecutionRequest request) {
        if (request == null || request.getRunId() == null || request.getRunId().isBlank()
                || request.getInitialDecision() == null || request.getConversationContext() == null
                || request.getExecutionContext() == null || request.getSupervisor() == null) {
            throw invalid("计划执行请求不完整");
        }
    }

    /**
     * 构造统一业务异常（409 CONFLICT），用于计划执行中的各类前置校验失败。
     *
     * @param message 异常消息
     * @return 业务异常
     */
    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatusEnum.CONFLICT, message);
    }

    /**
     * 步骤尝试终态载体：状态 + 成功结果或失败编码。
     * <p>成功时 result 为步骤输出内容、errorCode 为空；失败时 result 为空、errorCode 为分类后的错误编码。</p>
     *
     * @param status    步骤终态
     * @param result    成功结果
     * @param errorCode 失败编码
     */
    private record StepAttemptOutcome(ExecutionStepStatus status, String result, String errorCode) {
    }

    /**
     * 步骤失败信号：携带失败步骤与分类后的失败信息，供主循环做重规划决策。
     * <p>作为执行引擎内部的控制流载体，仅由步骤执行链路抛出，
     * 用于区分“可重试失败（触发重规划）”与“不可重试失败（终止计划）”。</p>
     */
    private static final class StepFailure extends RuntimeException {
        private final PlannerStep step;
        private final ExecutionFailure failure;

        /**
         * 以错误编码构造不可重试的步骤失败，用于前置门禁校验（调用次数、副作用重复等）。
         *
         * @param step      失败步骤
         * @param errorCode 失败编码
         */
        private StepFailure(PlannerStep step, String errorCode) {
            this(step, new ExecutionFailure(errorCode, "计划步骤执行失败", false), null);
        }

        /**
         * 以分类后的失败信息构造步骤失败，可重试性取决于失败分类结果。
         *
         * @param step    失败步骤
         * @param failure 分类后的失败信息（含可重试性与客户端可读消息）
         * @param cause   底层异常
         */
        private StepFailure(PlannerStep step, ExecutionFailure failure, Throwable cause) {
            super(failure.code(), cause);
            this.step = Objects.requireNonNull(step);
            this.failure = Objects.requireNonNull(failure);
        }

        /**
         * 生成失败观察摘要，作为重规划时传递给 Planner 的失败上下文。
         *
         * @return 步骤 ID、能力编码、错误编码与失败原因的拼接描述
         */
        private String observation() {
            return "stepId=" + step.getId() + ", capabilityCode=" + step.getCapabilityCode()
                    + ", errorCode=" + failure.code() + ", reason=" + failure.clientMessage();
        }
    }
}

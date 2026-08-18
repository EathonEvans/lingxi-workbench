package com.lingxi.workbench.application.runtime.service.plan;

import com.lingxi.workbench.agentruntime.planner.PlannerClarification;
import com.lingxi.workbench.agentruntime.planner.PlannerDecision;
import com.lingxi.workbench.agentruntime.planner.PlannerDecisionAction;
import com.lingxi.workbench.agentruntime.planner.PlannerInteractionType;
import com.lingxi.workbench.agentruntime.planner.PlannerRunResult;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.model.ExecutionEventCommand;
import com.lingxi.workbench.application.runtime.model.PlanExecutionRequest;
import com.lingxi.workbench.application.runtime.model.PlanExecutionResult;
import com.lingxi.workbench.application.runtime.model.PlannerRequest;
import com.lingxi.workbench.application.runtime.model.PlannerRuntimeRequest;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionFailureClassifier;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionPersistenceService;
import com.lingxi.workbench.application.runtime.service.plan.PlanExecutionService;
import com.lingxi.workbench.application.runtime.service.plan.PlannerRuntimeCoordinator;
import com.lingxi.workbench.application.runtime.service.plan.PlannerService;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * Planner 运行协调实现。
 *
 * <p>作为 Planner 编排的协调器，统一编排运行记录落库、Planner 决策、决策执行与澄清会话恢复：
 * 先持久化运行开始信息并发布“规划中”事件，再根据上下文工作预算决定是否调用 PlannerService 决策，
 * 最后把初始决策交给 PlanExecutionService 执行。</p>
 *
 * <p>上下文工作预算耗尽时不调用 Planner，直接构造可恢复的澄清会话；Planner 决策失败时
 * 区分用户取消与运行时异常，事件写入失败则抑制为原始异常的附加异常，保留原始失败语义。</p>
 */
@Service
@RequiredArgsConstructor
public class PlannerRuntimeCoordinatorImpl implements PlannerRuntimeCoordinator {

    private final ExecutionPersistenceService persistenceService;
    private final PlannerService plannerService;
    private final PlanExecutionService planExecutionService;
    private final ExecutionFailureClassifier failureClassifier;

    /**
     * 执行一次 Planner 编排运行：先落库运行开始信息并发布“规划中”事件，
     * 再根据上下文工作预算决定是否调用 Planner 决策，最终将初始决策交给执行服务。
     *
     * <p>上下文工作预算耗尽时跳过 Planner，直接以可恢复的澄清会话结束本轮；
     * Planner 决策失败时区分“用户取消”与“运行时异常”两类失败，统一发布失败事件后原样抛出。</p>
     *
     * @param request Planner 运行请求，包含运行标识、会话上下文与执行上下文
     * @return 计划执行结果
     */
    @Override
    public PlanExecutionResult execute(PlannerRuntimeRequest request) {
        // 顶级运行已在 Application 调度入口统一创建，Planner 只追加计划与步骤事实。
        // 上下文工作预算已耗尽：跳过 Planner 决策，直接以澄清会话结束本轮，避免预算不足时继续消耗模型调用。
        if (request.getExecutionContext().shouldFinalize()) {
            return planExecutionService.execute(planRequest(request, budgetBoundaryClarification()));
        }
        PlannerRunResult plannerResult;
        // 调用 Planner 生成初始决策；取消与运行时异常分开处理，保证用户取消语义不被吞掉。
        try {
            plannerResult = plannerService.decide(PlannerRequest.builder()
                    .conversationContext(request.getConversationContext())
                    .executionContext(request.getExecutionContext())
                    .planNo(1)
                    .confirmedCapabilityCodes(request.confirmedCapabilityCodes())
                    .supervisor(request.getSupervisor())
                    .build());
            // 将 Planner 决策消耗的 token 记入上下文，纳入本轮工作预算统计。
            request.getExecutionContext().recordUsage(plannerResult.tokenUsage());
        } catch (CancellationException exception) {
            publishInitialDecisionFailure(request, ExecutionEventType.RUN_CANCELLED, "规划已取消", exception);
            throw exception;
        } catch (RuntimeException exception) {
            publishInitialDecisionFailure(request, ExecutionEventType.RUN_FAILED,
                    failureClassifier.classify(exception).clientMessage(), exception);
            throw exception;
        }
        return planExecutionService.execute(planRequest(request, plannerResult.decision()));
    }

    /**
     * 组装提交给执行服务的计划执行请求。
     *
     * <p>把初始决策与运行标识、会话上下文、执行上下文、已确认能力码和执行器统一打包，
     * 供正常决策与预算边界澄清两条路径复用。</p>
     *
     * @param request  Planner 运行请求
     * @param decision 初始执行决策（Planner 决策或预算边界澄清）
     * @return 计划执行请求
     */
    private static PlanExecutionRequest planRequest(PlannerRuntimeRequest request, PlannerDecision decision) {
        return PlanExecutionRequest.builder()
                .runId(request.getRunId())
                .initialDecision(decision)
                .conversationContext(request.getConversationContext())
                .executionContext(request.getExecutionContext())
                .confirmedCapabilityCodes(request.confirmedCapabilityCodes())
                .supervisor(request.getSupervisor())
                .build();
    }

    /** 上下文准备已耗尽工作预算时不再调用Planner，直接建立可恢复的澄清会话。 */
    private static PlannerDecision budgetBoundaryClarification() {
        return PlannerDecision.builder()
                .action(PlannerDecisionAction.CLARIFY)
                .clarification(PlannerClarification.builder()
                        .question("本轮上下文处理已达到预算边界，请缩小问题范围或开启新对话后继续。")
                        .options(List.of())
                        .interactionType(PlannerInteractionType.CLARIFICATION)
                        .capabilityCodes(List.of())
                        .build())
                .build();
    }

    /**
     * 发布 Planner 初始决策失败事件：同时写入持久化事件与实时推送事件。
     *
     * <p>事件写入失败时抑制为原始异常的附加异常，保证原始决策失败不被掩盖；
     * 本方法自身不抛出异常，避免在异常处理路径中二次失败。</p>
     *
     * @param request  Planner 运行请求，提供运行标识与事件发布器
     * @param type     失败事件类型（取消或失败）
     * @param message  面向客户端的失败消息
     * @param original 原始异常，事件写入失败将作为 suppressed 附加到其上
     */
    private void publishInitialDecisionFailure(PlannerRuntimeRequest request, ExecutionEventType type,
                                               String message, RuntimeException original) {
        // 事件写入失败不能掩盖原始决策失败：抑制为原始异常的附加异常后继续抛出。
        try {
            persistenceService.appendEvent(ExecutionEventCommand.builder()
                    .runId(request.getRunId())
                    .eventType(type)
                    .message(message)
                    .build());
            request.getExecutionContext().getEventPublisher().publishExecution(ChatExecutionTimelineEventBO.builder()
                    .runId(request.getRunId())
                    .eventType(type)
                    .message(message)
                    .build());
        } catch (RuntimeException persistenceFailure) {
            original.addSuppressed(persistenceFailure);
        }
    }

}

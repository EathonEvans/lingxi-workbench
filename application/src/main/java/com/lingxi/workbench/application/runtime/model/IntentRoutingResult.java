package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingReason;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 意图路由的确定性门控结果，由 {@code IntentRoutingServiceImpl.route()} 返回。
 *
 * <p>该结果包含上层调度器需要的全部信息：</p>
 * <ul>
 *   <li>该做什么（action）—— 执行 Workflow / Agent / 固定答复 / 澄清 / 交由模型</li>
 *   <li>目标是什么（mapping）—— 发布快照中的映射记录</li>
 *   <li>用什么输入（executionQuery）—— 原始问题或拼接了槽位的增强查询</li>
 *   <li>如果澄清（clarification）—— 问题文本、选项列表、轮次、过期时间</li>
 *   <li>如果固定答复（response）—— 直接返回的文本</li>
 * </ul>
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentRoutingResult {

    /**
     * 上层调度器应执行的动作
     */
    private final IntentRoutingAction action;

    /**
     * 确定的意图编码（EXECUTE_WORKFLOW / EXECUTE_AGENT 时有值）
     */
    private final String intentCode;

    /**
     * 发布快照中的映射记录，包含 targetType、targetId、targetVersionId
     */
    private final ApplicationIntentMapping mapping;

    /**
     * 执行时使用的查询文本：原始问题或拼接了已收集槽位的增强版
     */
    private final String executionQuery;

    /**
     * 固定答复文本（FIXED_RESPONSE 时有值）
     */
    private final String response;

    /**
     * 结构化澄清事件（CLARIFICATION 时有值），包含 question、options、round、expiresAt
     */
    private final ChatClarificationEventBO clarification;

    /** 未直达时提供给 fallback Agent 的安全结构化证据。 */
    private final IntentRoutingEvidence routingEvidence;

    /** 本次不可变路由决策事实主键。 */
    private final Long decisionId;

    /** 本次路由语义模型消耗；无模型调用时为空用量。 */
    private final TokenUsage tokenUsage;

    /** 本次路由语义模型调用次数，包含 Spring AI 结构化输出修复调用。 */
    private final int modelCalls;

    /** 本次路由语义模型重试次数。 */
    private final int retryCount;

    /** 本次路由语义模型总耗时。 */
    private final long durationMillis;

    /**
     * 当前请求未进入意图路由。
     */
    public static IntentRoutingResult skipped(String query) {
        return builder()
                .action(IntentRoutingAction.SKIPPED)
                .executionQuery(query)
                .tokenUsage(TokenUsage.empty())
                .routingEvidence(IntentRoutingEvidence.builder()
                        .reason(IntentRoutingReason.ROUTING_DISABLED)
                        .candidates(java.util.List.of())
                        .build())
                .build();
    }

    /**
     * 意图路由未选择执行目标时交还上层模型决策。
     */
    public static IntentRoutingResult deferUnmatched(String query, IntentRoutingReason reason) {
        return builder()
                .action(IntentRoutingAction.DEFER_TO_AGENT)
                .executionQuery(query)
                .tokenUsage(TokenUsage.empty())
                .routingEvidence(IntentRoutingEvidence.builder()
                        .reason(reason)
                        .candidates(java.util.List.of())
                        .build())
                .build();
    }

    /**
     * 意图路由无法安全直达时，将裁剪后的候选证据交给 fallback Agent 二次判断。
     */
    public static IntentRoutingResult defer(String query, IntentRoutingEvidence evidence) {
        return builder()
                .action(IntentRoutingAction.DEFER_TO_AGENT)
                .executionQuery(query)
                .routingEvidence(evidence)
                .tokenUsage(TokenUsage.empty())
                .build();
    }
}

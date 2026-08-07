package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
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
 *
 * <p>{@code workflow()} 静态工厂方法用于兼容未启用意图路由的已有 Application，
 * 直接返回 EXECUTE_WORKFLOW 动作，query 原样透传。</p>
 */
@Getter
@Builder
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

    /**
     * 创建兼容旧模式的直通 Workflow 结果。
     * 用于 Application 未启用意图路由时的默认行为。
     *
     * @param intentCode 意图编码，可为 null
     * @param query      原始用户查询
     * @return 指示执行历史内嵌 Workflow 的兼容结果
     */
    public static IntentRoutingResult workflow(String intentCode, String query) {
        return builder()
                .action(IntentRoutingAction.EXECUTE_WORKFLOW)
                .intentCode(intentCode)
                .executionQuery(query)
                .build();
    }

    /** 可选快速路由未命中时交还上层模型决策。 */
    public static IntentRoutingResult noMatch(String query) {
        return builder()
                .action(IntentRoutingAction.NO_MATCH)
                .executionQuery(query)
                .build();
    }
}

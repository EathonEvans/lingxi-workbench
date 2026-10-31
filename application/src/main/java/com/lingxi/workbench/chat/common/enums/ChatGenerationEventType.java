package com.lingxi.workbench.chat.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 模型生成任务事件类型。
 */
@Getter
@RequiredArgsConstructor
public enum ChatGenerationEventType {
    /**
     * 模型生成的文本增量。
     */
    CHUNK("chunk", "文本增量"),
    /**
     * Agent 工具执行状态。
     */
    TOOL("tool", "工具状态"),
    /**
     * Agent 不包含思维链的安全阶段进度。
     */
    PROGRESS("progress", "Agent进度"),
    /**
     * 需要用户补充意图或槽位。
     */
    CLARIFICATION("clarification", "澄清问题"),
    /**
     * Tool 或计划等待用户决定。
     */
    INTERACTION_REQUIRED("interaction_required", "等待人工决定"),
    /**
     * 人工决定已经生效，原运行准备恢复。
     */
    INTERACTION_RESOLVED("interaction_resolved", "人工决定已生效"),
    /**
     * Planner开始构建执行决策。
     */
    PLANNING("planning", "正在规划"),
    /**
     * Planner返回可展示计划。
     */
    PLAN("plan", "执行计划"),
    /**
     * 计划步骤状态变化。
     */
    STEP("step", "计划步骤"),
    /**
     * 计划步骤产生的安全文本增量。
     */
    STEP_OUTPUT("step_output", "步骤输出"),
    /**
     * 步骤失败后开始有限重规划。
     */
    REPLANNING("replanning", "正在重规划"),
    /** 从数据库恢复的已生成回答快照。 */
    SNAPSHOT("snapshot", "回答快照"),
    /**
     * 回答完成后服务端主动给出的后续建议。
     */
    SUGGESTION("suggestion", "主动建议"),
    /**
     * 模型回答生成完成。
     */
    COMPLETED("completed", "生成完成"),
    /**
     * 模型调用失败。
     */
    FAILED("failed", "生成失败"),
    /**
     * 用户主动取消模型生成。
     */
    CANCELLED("cancelled", "已取消");

    /**
     * 稳定编码。
     */
    private final String code;
    /**
     * 中文说明。
     */
    private final String describe;
}

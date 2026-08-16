package com.lingxi.workbench.chat.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 模型生成任务事件类型。 */
@Getter
@RequiredArgsConstructor
public enum ChatGenerationEventType {
    /** 模型生成的文本增量。 */
    CHUNK("CHUNK", "文本增量"),
    /** Agent 工具执行状态。 */
    TOOL("TOOL", "工具状态"),
    /** Agent 不包含思维链的安全阶段进度。 */
    PROGRESS("PROGRESS", "Agent进度"),
    /** 需要用户补充意图或槽位。 */
    CLARIFICATION("CLARIFICATION", "澄清问题"),
    /** Tool 或计划等待用户决定。 */
    INTERACTION_REQUIRED("INTERACTION_REQUIRED", "等待人工决定"),
    /** 人工决定已经生效，原运行准备恢复。 */
    INTERACTION_RESOLVED("INTERACTION_RESOLVED", "人工决定已生效"),
    /** Planner开始构建执行决策。 */
    PLANNING("PLANNING", "正在规划"),
    /** Planner返回可展示计划。 */
    PLAN("PLAN", "执行计划"),
    /** 计划步骤状态变化。 */
    STEP("STEP", "计划步骤"),
    /** 计划步骤产生的安全文本增量。 */
    STEP_OUTPUT("STEP_OUTPUT", "步骤输出"),
    /** 步骤失败后开始有限重规划。 */
    REPLANNING("REPLANNING", "正在重规划"),
    /** 从数据库恢复的完整或部分回答快照。 */
    SNAPSHOT("SNAPSHOT", "回答快照"),
    /** 模型回答生成完成。 */
    COMPLETED("COMPLETED", "生成完成"),
    /** 达到运行边界后保留已有可用内容并降级完成。 */
    PARTIAL("PARTIAL", "部分完成"),
    /** 模型调用失败。 */
    FAILED("FAILED", "生成失败"),
    /** 用户主动取消模型生成。 */
    CANCELLED("CANCELLED", "已取消");

    /** 稳定编码。 */
    private final String code;
    /** 中文说明。 */
    private final String describe;
}

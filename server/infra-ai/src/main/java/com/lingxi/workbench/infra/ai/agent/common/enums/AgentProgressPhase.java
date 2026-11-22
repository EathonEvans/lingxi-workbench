package com.lingxi.workbench.infra.ai.agent.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Agent 执行过程中允许向用户展示的安全阶段。 */
@Getter
@RequiredArgsConstructor
public enum AgentProgressPhase {
    ANALYZING("ANALYZING", "正在分析问题"),
    USING_TOOLS("USING_TOOLS", "正在执行所需操作"),
    EVALUATING("EVALUATING", "正在评估已获得的信息"),
    FINALIZING("FINALIZING", "正在整理回答");

    private final String code;
    private final String describe;
}

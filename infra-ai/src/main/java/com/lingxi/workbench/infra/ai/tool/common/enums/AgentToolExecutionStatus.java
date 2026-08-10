package com.lingxi.workbench.infra.ai.tool.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Agent 工具单次执行状态。
 */
@Getter
@RequiredArgsConstructor
public enum AgentToolExecutionStatus {

    RUNNING("RUNNING", "执行中"),
    SUCCEEDED("SUCCEEDED", "执行成功"),
    REUSED("REUSED", "复用已有结果"),
    FAILED("FAILED", "执行失败");

    /** 稳定编码。 */
    private final String code;
    /** 中文说明。 */
    private final String describe;
}

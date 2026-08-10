package com.lingxi.workbench.infra.ai.tool.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Agent Tool 的执行语义，用于决定重复调用能否安全复用。
 */
@Getter
@RequiredArgsConstructor
public enum AgentToolExecutionMode {

    DETERMINISTIC_READ_ONLY("DETERMINISTIC_READ_ONLY", "确定性只读"),
    DYNAMIC_READ_ONLY("DYNAMIC_READ_ONLY", "动态只读"),
    SIDE_EFFECT("SIDE_EFFECT", "存在副作用");

    /** 稳定编码。 */
    private final String code;
    /** 中文说明。 */
    private final String describe;

    /** 相同入参在单次执行内是否可以安全复用已有结果。 */
    public boolean canReuseResult() {
        return this == DETERMINISTIC_READ_ONLY;
    }
}

package com.lingxi.workbench.observability.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Agent 链路需要追加持久化的关键观测事件类型。 */
@Getter
@RequiredArgsConstructor
public enum AiObservationEventType {
    RUN_STARTED("RUN_STARTED", "运行开始"),
    RUN_COMPLETED("RUN_COMPLETED", "运行完成"),
    RUN_FAILED("RUN_FAILED", "运行失败"),
    MODEL_CALL("MODEL_CALL", "模型调用"),
    TOOL_CALL("TOOL_CALL", "工具调用"),
    STAGE_COMPLETED("STAGE_COMPLETED", "阶段完成"),
    EXECUTION_COMPLETED("EXECUTION_COMPLETED", "目标执行完成");

    @EnumValue
    private final String code;
    private final String describe;
}

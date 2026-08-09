package com.lingxi.workbench.workflow.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Workflow 可恢复运行事件类型。 */
@Getter
@RequiredArgsConstructor
public enum WorkflowRunEventType {
    RUN_STARTED("RUN_STARTED", "运行开始"),
    NODE_STARTED("NODE_STARTED", "节点开始"),
    NODE_SUCCEEDED("NODE_SUCCEEDED", "节点成功"),
    NODE_FAILED("NODE_FAILED", "节点失败"),
    RUN_SUCCEEDED("RUN_SUCCEEDED", "运行成功"),
    RUN_FAILED("RUN_FAILED", "运行失败");

    @EnumValue
    private final String code;
    private final String describe;
}

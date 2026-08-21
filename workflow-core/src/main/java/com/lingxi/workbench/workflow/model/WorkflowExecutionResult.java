package com.lingxi.workbench.workflow.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流执行结果，聚合单次运行的全部输出和节点追踪记录。
 *
 * @param executionId 本次执行的唯一标识
 * @param outputs     END 节点按 outputMapping 提取的最终输出变量，不可变
 * @param traces      每个节点的执行追踪记录列表，按执行顺序排列，不可变
 */
public record WorkflowExecutionResult(
        String executionId,
        Map<String, Object> outputs,
        List<WorkflowNodeTrace> traces) {

    public WorkflowExecutionResult {
        outputs = outputs == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
        traces = traces == null ? List.of() : List.copyOf(traces);
    }
}

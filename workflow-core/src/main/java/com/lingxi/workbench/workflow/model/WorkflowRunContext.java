package com.lingxi.workbench.workflow.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

/** 单次 Workflow 运行的不可变监听上下文。 */
@Getter
public final class WorkflowRunContext {

    private final String runId;
    private final WorkflowDefinition definition;
    private final WorkflowExecutionRequest request;
    private final WorkflowExecutionPorts ports;
    private final Instant startedAt;

    @Builder
    private WorkflowRunContext(String runId, WorkflowDefinition definition, WorkflowExecutionRequest request,
                               WorkflowExecutionPorts ports, Instant startedAt) {
        this.runId = requireText(runId, "Workflow runId不能为空");
        this.definition = Objects.requireNonNull(definition, "Workflow定义不能为空");
        this.request = Objects.requireNonNull(request, "Workflow执行请求不能为空");
        this.ports = Objects.requireNonNull(ports, "Workflow执行边界不能为空");
        this.startedAt = Objects.requireNonNull(startedAt, "Workflow开始时间不能为空");
    }

    private static String requireText(String value, String message) {
        if (Objects.isNull(value) || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}

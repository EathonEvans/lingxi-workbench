package com.lingxi.workbench.workflow.model;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节点执行追踪记录，保存单次执行中一个节点的输入、输出及状态快照。
 *
 * <p>追踪对象一经创建即不可变。八个字段按照“节点身份、执行状态、时间、
 * 数据、异常”分组，通过 Builder 构造，调用点能够直接读出每个值的语义。</p>
 */
@Getter
public class WorkflowNodeTrace implements Serializable {

    /**
     * 节点在 Workflow 定义中的唯一 ID。
     */
    private final String nodeId;
    /**
     * 节点类型。
     */
    private final WorkflowNodeType nodeType;
    /**
     * 执行状态。
     */
    private final WorkflowNodeStatus status;
    /**
     * 节点开始执行的时间。
     */
    private final Instant startedAt;
    /**
     * 节点完成执行的时间。
     */
    private final Instant finishedAt;
    /**
     * 节点解析后的不可变输入变量。
     */
    private final Map<String, Object> inputs;
    /**
     * 节点执行后的不可变输出变量。
     */
    private final Map<String, Object> outputs;
    /**
     * 失败时的错误信息，成功时为空。
     */
    private final String errorMessage;
    /** 意图分类节点的稳定决策状态，其他节点为空。 */
    private final IntentClassifierNodeStatus decisionStatus;
    /** 意图分类节点的稳定原因编码，其他节点为空。 */
    private final String reasonCode;
    /** 本节点使用的模型实例，未调用模型时为空。 */
    private final Long modelInstanceId;
    /** 本节点使用的 PromptVersion，未调用模型时为空。 */
    private final Long promptVersionId;
    /** 本节点实际模型 Token 用量。 */
    private final TokenUsage tokenUsage;
    /** 本节点实际模型调用次数。 */
    private final int modelCalls;
    /** 本节点结构化修复次数。 */
    private final int retryCount;

    @Builder
    private WorkflowNodeTrace(
            String nodeId,
            WorkflowNodeType nodeType,
            WorkflowNodeStatus status,
            Instant startedAt,
            Instant finishedAt,
            Map<String, Object> inputs,
            Map<String, Object> outputs,
            String errorMessage,
            IntentClassifierNodeStatus decisionStatus,
            String reasonCode,
            Long modelInstanceId,
            Long promptVersionId,
            TokenUsage tokenUsage,
            int modelCalls,
            int retryCount) {
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.status = status;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.inputs = immutableCopy(inputs);
        this.outputs = immutableCopy(outputs);
        this.errorMessage = errorMessage;
        this.decisionStatus = decisionStatus;
        this.reasonCode = reasonCode;
        this.modelInstanceId = modelInstanceId;
        this.promptVersionId = promptVersionId;
        this.tokenUsage = java.util.Objects.requireNonNullElseGet(tokenUsage, TokenUsage::empty);
        this.modelCalls = modelCalls;
        this.retryCount = retryCount;
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}

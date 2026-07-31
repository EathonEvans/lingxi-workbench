package com.lingxi.workbench.infra.ai.tool.model;

import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * 单次工具执行向上层发布的安全状态事件。
 *
 * <p>该事件只包含前端展示和链路定位所需的元数据，不携带模型生成的工具参数、
 * 工具原始结果或异常堆栈，避免通过 SSE 泄露业务数据。</p>
 */
@Getter
@Builder
public class AgentToolExecutionEvent {

    /**
     * 服务端生成的单次工具调用标识。
     */
    private final String invocationId;
    /**
     * 工具稳定编码，与 OpenAI Function name 一致。
     */
    private final String toolCode;
    /**
     * 面向用户的工具展示名称。
     */
    private final String displayName;
    /**
     * 当前执行状态。
     */
    private final AgentToolExecutionStatus status;
    /**
     * 已完成或失败时的执行耗时，执行中为空。
     */
    private final Long durationMillis;
    /**
     * 失败时的稳定错误码，成功和执行中为空。
     */
    private final String errorCode;
}

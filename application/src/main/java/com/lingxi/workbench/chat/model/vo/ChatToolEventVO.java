package com.lingxi.workbench.chat.model.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * SSE 工具执行状态。
 */
@Getter
@Builder
public class ChatToolEventVO {

    /** 当前生成任务内的事件序号。 */
    private final long sequence;
    /** 服务端生成的单次工具调用标识。 */
    private final String invocationId;
    /** 工具稳定编码。 */
    private final String toolCode;
    /** 面向用户的工具名称。 */
    private final String displayName;
    /** RUNNING、SUCCEEDED 或 FAILED。 */
    private final String status;
    /** 工具执行耗时，执行中为空。 */
    private final Long durationMillis;
    /** 失败时的安全错误码。 */
    private final String errorCode;
    /** 所属计划修订号；根 Agent Tool 为空。 */
    private final Integer planNo;
    /** 所属计划步骤；根 Agent Tool 为空。 */
    private final String stepId;
}

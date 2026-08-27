package com.lingxi.workbench.chat.model.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * SSE 子 Agent 委派生命周期事件。
 *
 * <p>仅暴露前端展示所需的安全元数据，不包含委派任务、成员回答、模型思维过程或工具参数。</p>
 */
@Getter
@Builder
public class ChatSubAgentEventVO {

    /** 当前生成任务内的事件序号。 */
    private final long sequence;
    /** 服务端生成的单次委派标识。 */
    private final String delegationId;
    /** 发布快照中冻结的成员 Agent 主键。 */
    private final String agentId;
    /** 面向用户的成员 Agent 名称。 */
    private final String agentName;
    /** RUNNING、SUCCEEDED 或 FAILED。 */
    private final String status;
    /** 委派执行耗时，执行中为空。 */
    private final Long durationMillis;
    /** 失败时的安全错误码。 */
    private final String errorCode;
    /** 所属计划修订号。 */
    private final Integer planNo;
    /** 所属计划步骤。 */
    private final String stepId;
}

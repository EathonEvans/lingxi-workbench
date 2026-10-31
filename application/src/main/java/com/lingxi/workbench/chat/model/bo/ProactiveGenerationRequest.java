package com.lingxi.workbench.chat.model.bo;

import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.PreResolvedDispatch;
import com.lingxi.workbench.framework.context.LoginUser;
import lombok.Builder;
import lombok.Getter;

/**
 * 定时触发器主动生成任务的启动入参。
 *
 * <p>由触发器消费侧组装：专属会话内以渲染后的任务目标创建一轮 TRIGGER 来源的对话，
 * 并以预解析分发直接执行绑定的能力。</p>
 */
@Getter
@Builder
public class ProactiveGenerationRequest {

    /** 触发器绑定的冻结 Application 快照。 */
    private final AppRuntimeSnapshot application;
    /** 触发器归属用户的登录上下文。 */
    private final LoginUser loginUser;
    /** 触发器主键。 */
    private final Long triggerId;
    /** 单次触发记录主键。 */
    private final Long fireId;
    /** 专属会话主键。 */
    private final Long conversationId;
    /** 幂等消息标识，格式 trigger:{triggerId}:{fireId}。 */
    private final String clientMessageId;
    /** 渲染后的任务目标，作为本轮执行问题。 */
    private final String query;
    /** 预解析分发目标（能力编码 + 类型 + 工具白名单）。 */
    private final PreResolvedDispatch preResolvedDispatch;
}

package com.lingxi.workbench.infra.ai.tool.model;

import lombok.Builder;
import lombok.Getter;

/**
 * 单次 Agent 回答过程中，工具可以读取的服务端可信执行上下文。
 *
 * <p>它与模型生成的工具参数承担不同职责：</p>
 *
 * <ul>
 *     <li>工具参数描述模型希望执行的业务动作，例如时区、订单号或搜索关键词；</li>
 *     <li>执行上下文描述“谁在什么任务中调用工具”，用于权限校验、数据隔离和审计。</li>
 * </ul>
 *
 * <p>这些字段由 Application 在进入 Agent 节点时，根据登录态、Workflow
 * 任务和已落库的会话消息创建。模型只能生成工具参数，不能声明或覆盖这里的用户、
 * 会话和消息标识，否则会形成越权访问风险。</p>
 *
 * <p>该对象只在一次 Agent 回答期间存活，不写入 AgentVersion，也不作为 Prompt
 * 内容发送给模型。Agent Runtime 通过 {@link AgentToolRequest} 将同一份可信信息
 * 传递给需要鉴权的工具实现。</p>
 */
@Getter
@Builder
public class AgentToolExecutionContext {

    /**
     * Workflow 本次运行的唯一标识，用于关联节点轨迹和工具执行日志。
     */
    private final String executionId;
    /**
     * 当前登录用户主键，来源于服务端登录上下文，是工具数据权限的主体。
     */
    private final Long userId;
    /**
     * 当前会话主键，来源于已校验归属关系的对话轮次。
     */
    private final Long conversationId;
    /**
     * 当前生成中的 Assistant 消息主键，用于关联本次回答及后续审计记录。
     */
    private final Long assistantMessageId;
}

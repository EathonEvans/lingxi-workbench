package com.lingxi.workbench.agentruntime.context;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.state.AgentState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 将 Application 已准备的对话记忆投影为 AgentScope 初始状态。 */
public final class AgentScopeStateFactory {

    private AgentScopeStateFactory() {
    }

    /**
     * 初始消息仅在新运行时构造一次；HITL 恢复必须改用 PostgreSQL Checkpoint 中的 AgentState。
     *
     * @param command   已完成 Prompt 渲染的模型命令
     * @param userId    已完成鉴权的用户标识
     * @param sessionId 当前 Agent 运行标识
     * @return AgentScope 可直接消费的初始对话状态
     */
    public static AgentState create(ChatStreamCommand command, String userId, String sessionId) {
        Objects.requireNonNull(command, "AgentScope模型命令不能为空");
        List<Msg> messages = new ArrayList<>();
        command.history().stream().map(AgentScopeStateFactory::historyMessage)
                .forEach(messages::add);
        messages.add(Msg.builderForRole(MsgRole.USER).textContent(command.prompt()).build());
        return AgentState.builder()
                .userId(requireText(userId, "AgentScope用户标识"))
                .sessionId(requireText(sessionId, "AgentScope会话标识"))
                // Lingxi 已在 Prompt 渲染阶段注入受预算约束的摘要。不能填入该字段：
                // AgentScope 2.0.2 会在 Tool 后启动未受 Lingxi 预算治理的默认摘要模型调用。
                .summary("")
                .context(messages)
                .build();
    }

    private static Msg historyMessage(ChatHistoryMessage message) {
        MsgRole role = switch (message.role()) {
            case USER -> MsgRole.USER;
            case ASSISTANT -> MsgRole.ASSISTANT;
        };
        return Msg.builderForRole(role).textContent(message.content()).build();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }
}

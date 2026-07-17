package com.lingxi.workbench.infra.ai.chat;

/** 模型调用所需的一条历史消息。 */
public record ChatHistoryMessage(Role role, String content) {

    /** 模型上下文支持的历史消息角色。 */
    public enum Role {
        USER,
        ASSISTANT
    }
}

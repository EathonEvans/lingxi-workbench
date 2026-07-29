package com.lingxi.workbench.infra.ai.chat.model.bo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 模型调用所需的一条历史消息。
 *
 * @param role 历史消息角色，目前只允许用户和助手消息
 * @param content 已完成消息的完整文本内容
 */
public record ChatHistoryMessage(Role role, String content) {

    /** 模型上下文支持的历史消息角色。 */
    @Getter
    @RequiredArgsConstructor
    public enum Role {
        /** 用户消息。 */
        USER("USER", "用户"),
        /** 助手消息。 */
        ASSISTANT("ASSISTANT", "助手");

        /** 角色稳定编码，用于程序交互和持久化。 */
        private final String code;
        /** 角色中文说明，仅用于界面展示。 */
        private final String describe;
    }
}

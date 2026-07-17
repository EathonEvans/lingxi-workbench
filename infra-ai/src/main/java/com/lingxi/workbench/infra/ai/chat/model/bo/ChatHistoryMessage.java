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
        USER("USER", "用户"),
        ASSISTANT("ASSISTANT", "助手");

        /** 稳定编码。 */
        private final String code;
        /** 中文说明。 */
        private final String describe;
    }
}

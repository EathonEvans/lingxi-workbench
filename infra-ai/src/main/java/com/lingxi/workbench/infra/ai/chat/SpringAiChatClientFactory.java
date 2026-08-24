package com.lingxi.workbench.infra.ai.chat;

import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * 统一创建 Spring AI ChatClient，使所有模型调用复用应用级观测注册表。
 */
@Component
@RequiredArgsConstructor
public class SpringAiChatClientFactory {

    private final ObservationRegistry observationRegistry;

    /**
     * 基于调用方已经解析并校验的 ChatModel 创建客户端。
     */
    public ChatClient create(ChatModel chatModel) {
        return ChatClient.builder(chatModel, observationRegistry, null, null).build();
    }
}

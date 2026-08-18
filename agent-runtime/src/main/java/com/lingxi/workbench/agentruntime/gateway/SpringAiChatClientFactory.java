package com.lingxi.workbench.agentruntime.gateway;

import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * 统一创建 Spring AI ChatClient，集中承载后续 Advisor 装配规则。
 */
@Component
@RequiredArgsConstructor
public class SpringAiChatClientFactory {

    private final ObservationRegistry observationRegistry;

    /**
     * 基于已解析的运行时模型创建请求客户端，保持模型实例与观测注册表一致。
     */
    public ChatClient create(ChatModel chatModel) {
        return ChatClient.builder(chatModel, observationRegistry, null, null).build();
    }
}

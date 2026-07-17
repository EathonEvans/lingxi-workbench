package com.lingxi.workbench.infra.ai.chat;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import reactor.core.publisher.Flux;

/**
 * 面向业务模块的流式对话能力。
 */
public interface StreamingChatGateway {

    /**
     * 流式生成模型回复。
     *
     * <p>返回的是冷流，只有上层主动订阅后才会真正创建客户端请求并调用模型供应商。</p>
     *
     * @param command 用户输入、窗口记忆及本次调用的模型运行配置
     * @return 文本增量和最终 Token 用量组成的模型输出流
     */
    Flux<ChatStreamChunk> stream(ChatStreamCommand command);
}

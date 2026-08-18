package com.lingxi.workbench.infra.ai.chat;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatCompletionResult;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatCompletionCommand;
import reactor.core.publisher.Mono;

/**
 * 面向业务模块的非流式模型调用能力。
 *
 * <p>问题重写、意图识别等非流式模型任务需要等待一个完整且可校验的结果，不应复用面向
 * 最终回答展示的流式网关。</p>
 */
public interface ChatCompletionGateway {

    /**
     * 完成一次非流式模型调用。
     *
     * @param command 系统指令、历史消息、当前输入和模型运行配置
     * @return 完整文本与 Token 用量
     */
    Mono<ChatCompletionResult<String>> complete(ChatCompletionCommand command);
}

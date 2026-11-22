package com.lingxi.workbench.infra.ai.chat.model.command;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;

import java.util.List;

/**
 * 非流式模型调用命令。
 *
 * @param prompt       当前用户输入
 * @param systemPrompt 本次任务的系统指令
 * @param history      已裁剪的结构化历史消息
 * @param modelConfig  本次调用的模型运行配置
 */
public record ChatCompletionCommand(
        String prompt,
        String systemPrompt,
        List<ChatHistoryMessage> history,
        ChatModelRuntimeConfig modelConfig) {

    /** 对可变历史列表做防御性复制。 */
    public ChatCompletionCommand {
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        history = history == null ? List.of() : List.copyOf(history);
    }
}

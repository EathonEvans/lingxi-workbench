package com.lingxi.workbench.infra.ai.chat.model.command;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;

import java.util.List;

/**
 * 流式模型调用命令。
 *
 * @param prompt 用户输入
 * @param systemPrompt application 为本次请求追加的系统约束
 * @param history 已按窗口限制裁剪的历史消息
 * @param modelConfig 本次调用使用的模型运行配置
 */
public record ChatStreamCommand(
        String prompt,
        String systemPrompt,
        List<ChatHistoryMessage> history,
        ChatModelRuntimeConfig modelConfig) {

    public ChatStreamCommand {
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        // 防御性拷贝：防止外部修改内部历史消息列表，同时将 null 转为空列表。
        history = history == null ? List.of() : List.copyOf(history);
    }

    /**
     * 便捷构造，不带系统提示词。
     *
     * @param prompt 用户输入
     * @param history 已裁剪的历史消息
     * @param modelConfig 模型运行配置
     */
    public ChatStreamCommand(
            String prompt,
            List<ChatHistoryMessage> history,
            ChatModelRuntimeConfig modelConfig) {
        this(prompt, "", history, modelConfig);
    }
}

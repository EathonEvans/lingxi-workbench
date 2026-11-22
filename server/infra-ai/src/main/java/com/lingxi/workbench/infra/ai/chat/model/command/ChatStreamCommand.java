package com.lingxi.workbench.infra.ai.chat.model.command;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 流式模型调用命令。
 *
 * <p>工具配置是可选能力。字段超过简单值对象上限后使用 Builder，避免继续扩展位置参数构造器。</p>
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatStreamCommand {

    /**
     * 当前用户输入。
     */
    private final String prompt;
    /**
     * Application 为本次请求追加的系统约束。
     */
    private final String systemPrompt;
    /**
     * 已按窗口限制裁剪的历史消息。
     */
    private final List<ChatHistoryMessage> history;
    /**
     * 本次调用使用的模型运行配置。
     */
    private final ChatModelRuntimeConfig modelConfig;
    /**
     * 可选的工具调用配置；为空表示执行普通流式回答。
     */
    private final AgentToolRuntimeConfig toolRuntimeConfig;

    /**
     * 兼容普通对话调用，不启用工具。
     */
    public ChatStreamCommand(String prompt, String systemPrompt, List<ChatHistoryMessage> history, ChatModelRuntimeConfig modelConfig) {
        this(prompt, systemPrompt, history, modelConfig, null);
    }

    /**
     * 兼容不带系统提示词的普通对话调用。
     */
    public ChatStreamCommand(String prompt, List<ChatHistoryMessage> history, ChatModelRuntimeConfig modelConfig) {
        this(prompt, "", history, modelConfig, null);
    }

    /**
     * 返回非空系统提示词。
     */
    public String systemPrompt() {
        return Objects.requireNonNullElse(systemPrompt, "");
    }

    /**
     * 返回不可变历史消息列表。
     */
    public List<ChatHistoryMessage> history() {
        return List.copyOf(Objects.requireNonNullElseGet(history, List::of));
    }

    public String prompt() {
        return prompt;
    }

    public ChatModelRuntimeConfig modelConfig() {
        return modelConfig;
    }

    /**
     * 返回本次模型调用的可选工具配置。
     *
     * <p>命令内部仍以可选字段存储，读取端通过 Optional 明确表达“普通对话没有工具配置”。</p>
     */
    public Optional<AgentToolRuntimeConfig> toolRuntimeConfig() {
        return Optional.ofNullable(toolRuntimeConfig);
    }
}

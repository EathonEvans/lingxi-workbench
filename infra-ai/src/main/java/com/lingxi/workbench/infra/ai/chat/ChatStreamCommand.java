package com.lingxi.workbench.infra.ai.chat;

/**
 * 流式模型调用命令。
 *
 * @param prompt 用户输入
 * @param modelConfig 本次调用使用的模型运行配置
 */
public record ChatStreamCommand(String prompt, ChatModelRuntimeConfig modelConfig) {}

package com.lingxi.workbench.chat.service;

import com.lingxi.workbench.chat.model.bo.PromptPair;
import com.lingxi.workbench.chat.model.ccontext.PromptContext;

public interface PromptBuilderService {

    /**
     * 渲染已发布 Prompt 变量，并根据用户问题和知识库召回片段构建模型输入。
     *
     * @param context Prompt 构建上下文，包含版本快照、变量上下文和召回片段
     * @return 拼接完成的用户消息和系统提示词
     */
    PromptPair build(PromptContext context);
}

package com.lingxi.workbench.chat.service;

import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;

/** 单轮对话前置处理链节点。 */
public interface ChatProcessNode {

    /**
     * 设置唯一后继节点。
     *
     * @param next 后继节点
     * @return 后继节点，便于连续装配处理链
     */
    ChatProcessNode next(ChatProcessNode next);

    /**
     * 执行当前节点，并在完成后继续执行后继节点。
     *
     * @param context 单轮对话共享上下文
     */
    void process(ModelChatContext context);
}

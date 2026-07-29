package com.lingxi.workbench.chat.service;

import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;

/**
 * 对话前置处理链入口。
 */
public class ChatProcessChain {

    private final ChatProcessNode head;

    public ChatProcessChain(ChatProcessNode head) {
        this.head = head;
    }

    /**
     * 从处理链头部节点开始执行整个对话前置处理流程。
     * 各节点按装配顺序依次处理同一份共享上下文。
     *
     * @param context 单轮对话共享上下文
     */
    public void process(ModelChatContext context) {
        head.process(context);
    }
}

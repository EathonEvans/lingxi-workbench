package com.lingxi.workbench.chat.service;

import com.lingxi.workbench.chat.common.enums.ChatProcessResult;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;

import java.util.Objects;

/**
 * 单向对话处理节点基类。
 *
 * <p>模板方法固定“当前节点 → 后继节点”的执行方向，具体节点只实现自己的业务逻辑。</p>
 */
public abstract class AbstractChatProcessNode implements ChatProcessNode {

    private ChatProcessNode next;

    @Override
    public final ChatProcessNode next(ChatProcessNode next) {
        if (this.next != null) {
            throw new IllegalStateException(
                    getClass().getSimpleName() + " 的后继节点已经设置");
        }
        this.next = Objects.requireNonNull(next, "后继节点不能为空");
        return this.next;
    }

    /**
     * 执行当前节点负责的单一处理阶段。
     */
    protected abstract ChatProcessResult processCurrent(ModelChatContext context);

    @Override
    public final void process(ModelChatContext context) {
        ModelChatContext requiredContext =
                Objects.requireNonNull(context, "对话处理上下文不能为空");
        ChatProcessResult result = processCurrent(requiredContext);

        /*
         * 当前节点只有明确返回 CONTINUE 才能进入后继节点。
         * 后续增加“直接回答”或“需要澄清”等结果时，可以在当前节点终止链路，
         * 不需要让每个业务节点都感知链表的装配细节。
         */
        if (next == null || result != ChatProcessResult.CONTINUE) {
            return;
        }
        next.process(requiredContext);
    }
}

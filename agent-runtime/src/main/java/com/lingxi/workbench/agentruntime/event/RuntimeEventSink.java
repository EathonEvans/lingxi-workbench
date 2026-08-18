package com.lingxi.workbench.agentruntime.event;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;

/** Application 对 Runtime 的事件、文本和取消状态适配边界。 */
public interface RuntimeEventSink {

    /** 同步接收并先持久化一条 Runtime 生命周期事件。 */
    void onEvent(ExecutionEvent event);

    /** 转发最终回答的文本增量或用量分片。 */
    void onText(ChatStreamChunk chunk);

    /** 当前 Assistant 生成任务是否已被显式取消。 */
    boolean isCancelled();
}

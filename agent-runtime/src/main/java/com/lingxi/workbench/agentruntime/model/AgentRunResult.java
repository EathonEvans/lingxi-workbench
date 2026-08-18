package com.lingxi.workbench.agentruntime.model;

import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;

/** Agent Runtime 的明确完成语义。 */
public sealed interface AgentRunResult permits AgentRunResult.Completed, AgentRunResult.Partial,
        AgentRunResult.Interrupted {

    String content();

    TokenUsage usage();

    long lastSequenceNo();

    /**
     * 完整成功。
     *
     * @param content        最终回答
     * @param usage          本次调用实际Token用量
     * @param lastSequenceNo 最后一个Runtime事件序号
     */
    record Completed(String content, TokenUsage usage, long lastSequenceNo) implements AgentRunResult {
    }

    /**
     * 时间边界到界但已有经过 Runtime 汇总的可用文本。
     *
     * @param content        可用的部分回答
     * @param usage          本次调用实际Token用量
     * @param lastSequenceNo 最后一个Runtime事件序号
     */
    record Partial(String content, TokenUsage usage, long lastSequenceNo) implements AgentRunResult {
    }

    /**
     * Runtime 已持久化检查点并等待人工决定，不是失败也没有最终回答。
     *
     * @param interruption  安全人工中断事实
     * @param usage         中断前实际Token用量
     * @param lastSequenceNo 最后一个Runtime事件序号
     */
    record Interrupted(RuntimeInterruption interruption, TokenUsage usage,
                       long lastSequenceNo) implements AgentRunResult {

        @Override
        public String content() {
            return "";
        }
    }
}

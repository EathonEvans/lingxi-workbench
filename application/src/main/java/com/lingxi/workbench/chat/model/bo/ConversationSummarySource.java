package com.lingxi.workbench.chat.model.bo;

import com.lingxi.workbench.chat.common.enums.MessageRole;

import java.util.List;
import java.util.Objects;

/**
 * 一次滚动会话摘要使用的稳定输入。
 *
 * @param previousSummary               上一版已生效摘要
 * @param previousCoveredUserSequenceNo 上一版摘要覆盖到的用户消息序号，首次生成时为空
 * @param coveredUserSequenceNo         本次输入最后覆盖到的用户消息序号
 * @param messages                      本次新增的完整问答消息
 */
public record ConversationSummarySource(
        String previousSummary,
        Integer previousCoveredUserSequenceNo,
        Integer coveredUserSequenceNo,
        List<ChatMemoryMessageBO> messages) {

    public ConversationSummarySource {
        previousSummary = Objects.requireNonNullElse(previousSummary, "").trim();
        Objects.requireNonNull(coveredUserSequenceNo, "摘要覆盖序号不能为空");
        messages = List.copyOf(Objects.requireNonNull(messages, "摘要消息不能为空"));
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("摘要消息不能为空");
        }
        if (messages.size() % 2 != 0) {
            throw new IllegalArgumentException("摘要消息必须由完整问答轮次组成");
        }
        ChatMemoryMessageBO lastUserMessage = messages.get(messages.size() - 2);
        ChatMemoryMessageBO lastAssistantMessage = messages.getLast();
        if (lastUserMessage.role() != MessageRole.USER
                || lastAssistantMessage.role() != MessageRole.ASSISTANT
                || !Objects.equals(lastUserMessage.sequenceNo(), coveredUserSequenceNo)) {
            throw new IllegalArgumentException("摘要覆盖序号与最后完整问答不一致");
        }
        if (Objects.nonNull(previousCoveredUserSequenceNo)
                && previousCoveredUserSequenceNo >= coveredUserSequenceNo) {
            throw new IllegalArgumentException("摘要覆盖序号必须向前推进");
        }
    }
}

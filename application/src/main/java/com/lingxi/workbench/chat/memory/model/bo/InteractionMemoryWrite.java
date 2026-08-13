package com.lingxi.workbench.chat.memory.model.bo;

import com.lingxi.workbench.chat.memory.model.MemoryData;
import lombok.Builder;
import lombok.Getter;

/** 一次关键交互记录及其向量的原子写入数据。 */
@Getter
@Builder
public class InteractionMemoryWrite {
    private final Long userId;
    private final Long conversationId;
    private final Long assistantMessageId;
    private final String summary;
    private final MemoryData entities;
    private final Long embeddingModelInstanceId;
    private final float[] embedding;
}

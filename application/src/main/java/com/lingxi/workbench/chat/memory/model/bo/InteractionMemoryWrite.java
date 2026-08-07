package com.lingxi.workbench.chat.memory.model.bo;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/** 一次关键交互记录及其向量的原子写入数据。 */
@Getter
@Builder
public class InteractionMemoryWrite {
    private final Long userId;
    private final Long conversationId;
    private final Long assistantMessageId;
    private final String summary;
    private final Map<String, Object> entities;
    private final Long embeddingModelInstanceId;
    private final float[] embedding;
}

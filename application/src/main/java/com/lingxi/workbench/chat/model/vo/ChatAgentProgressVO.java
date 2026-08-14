package com.lingxi.workbench.chat.model.vo;

import lombok.Builder;
import lombok.Getter;

/** SSE Agent 安全阶段进度。 */
@Getter
@Builder
public class ChatAgentProgressVO {

    private final long sequence;
    private final String phase;
    private final String message;
    private final int roundNo;
    private final int toolCallCount;
}

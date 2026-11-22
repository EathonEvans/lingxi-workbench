package com.lingxi.workbench.application.runtime.model.message;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** RocketMQ 中只保存恢复任务所需的业务定位信息。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class HitlResumeMessage {

    private String eventId;
    private Long interactionId;
    private Long conversationId;
    private Long assistantMessageId;
    private Long appVersionId;
}

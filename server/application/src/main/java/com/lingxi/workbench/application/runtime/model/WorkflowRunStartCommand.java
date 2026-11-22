package com.lingxi.workbench.application.runtime.model;

import lombok.Builder;
import lombok.Getter;

/** Workflow运行持久化所需的已校验归属事实。 */
@Builder
@Getter
public class WorkflowRunStartCommand {

    private final Long assistantMessageId;
    private final Long conversationId;
    private final Long userId;
    private final Long applicationId;
    private final Long applicationVersionId;

    private WorkflowRunStartCommand(Long assistantMessageId, Long conversationId, Long userId,
                                    Long applicationId, Long applicationVersionId) {
        if (assistantMessageId == null || conversationId == null || userId == null
                || applicationId == null || applicationVersionId == null) {
            throw new IllegalArgumentException("Workflow运行归属事实不能为空");
        }
        this.assistantMessageId = assistantMessageId;
        this.conversationId = conversationId;
        this.userId = userId;
        this.applicationId = applicationId;
        this.applicationVersionId = applicationVersionId;
    }
}

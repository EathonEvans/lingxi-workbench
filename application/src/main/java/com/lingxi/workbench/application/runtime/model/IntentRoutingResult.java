package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 运行时意图识别、澄清和兜底门控的确定性结果。
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentRoutingResult {
    private final IntentRoutingAction action;
    private final String intentCode;
    private final ApplicationIntentMapping mapping;
    private final String executionQuery;
    private final String response;
    private final ChatClarificationEventBO clarification;

    public static IntentRoutingResult workflow(String intentCode, String query) {
        return builder()
                .action(IntentRoutingAction.EXECUTE_WORKFLOW)
                .intentCode(intentCode)
                .executionQuery(query)
                .build();
    }
}

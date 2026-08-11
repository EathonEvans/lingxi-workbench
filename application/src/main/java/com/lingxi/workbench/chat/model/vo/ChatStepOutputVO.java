package com.lingxi.workbench.chat.model.vo;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 用户端可展示的计划步骤文本增量。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatStepOutputVO {

    private long sequence;
    private String runId;
    private Integer planNo;
    private String stepId;
    private String content;
}

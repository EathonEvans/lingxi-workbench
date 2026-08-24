package com.lingxi.workbench.admin.application.model.vo;

import lombok.Data;

/** Application 意图草稿测试中的候选证据。 */
@Data
public class ApplicationIntentPreviewCandidateVO {

    private String intentCode;
    private String name;
    private double similarity;
    private int rank;
}

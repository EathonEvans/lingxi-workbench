package com.lingxi.workbench.admin.application.model.vo;

import lombok.Data;

/** 意图草稿测试返回的已校验槽位。 */
@Data
public class ApplicationIntentPreviewSlotVO {

    private String slotCode;
    private ApplicationIntentPreviewSlotValueVO value;
}

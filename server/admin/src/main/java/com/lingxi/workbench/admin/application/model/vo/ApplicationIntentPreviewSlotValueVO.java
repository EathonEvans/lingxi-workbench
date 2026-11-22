package com.lingxi.workbench.admin.application.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 意图草稿测试返回的强类型槽位值。 */
@Data
public class ApplicationIntentPreviewSlotValueVO {

    private String stringValue;
    private BigDecimal numberValue;
    private LocalDate dateValue;
    private Boolean booleanValue;
}

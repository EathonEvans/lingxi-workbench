package com.lingxi.workbench.application.runtime.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 模型识别出的强类型槽位值；服务端要求且只允许一个类型字段非空。
 */
@Data
@NoArgsConstructor
public class IntentSemanticSlotValue {

    @JsonProperty(required = false)
    private String stringValue;
    @JsonProperty(required = false)
    private BigDecimal numberValue;
    @JsonProperty(required = false)
    private LocalDate dateValue;
    @JsonProperty(required = false)
    private Boolean booleanValue;
}

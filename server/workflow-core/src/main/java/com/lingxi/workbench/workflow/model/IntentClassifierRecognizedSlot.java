package com.lingxi.workbench.workflow.model;

import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Workflow 状态中的已校验意图槽位，四种值字段必须且只能存在一个。 */
@Getter
public class IntentClassifierRecognizedSlot implements Serializable {

    private final String slotCode;
    private final String stringValue;
    private final BigDecimal numberValue;
    private final LocalDate dateValue;
    private final Boolean booleanValue;

    @Builder
    private IntentClassifierRecognizedSlot(String slotCode, String stringValue, BigDecimal numberValue,
                                           LocalDate dateValue, Boolean booleanValue) {
        if (Objects.isNull(slotCode) || slotCode.isBlank()) {
            throw new IllegalArgumentException("意图槽位编码不能为空");
        }
        int populatedValues = countNonNull(stringValue, numberValue, dateValue, booleanValue);
        if (populatedValues != 1) {
            throw new IllegalArgumentException("意图槽位必须且只能包含一个值");
        }
        this.slotCode = slotCode;
        this.stringValue = stringValue;
        this.numberValue = numberValue;
        this.dateValue = dateValue;
        this.booleanValue = booleanValue;
    }

    private static int countNonNull(Object... values) {
        int count = 0;
        for (Object value : values) {
            if (Objects.nonNull(value)) {
                count++;
            }
        }
        return count;
    }
}

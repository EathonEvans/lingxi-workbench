package com.lingxi.workbench.workflow.model.intent;

/** 意图槽位支持的基础值类型。 */
public enum IntentSlotValueType {

    STRING("STRING", "文本"),
    NUMBER("NUMBER", "数字"),
    DATE("DATE", "日期"),
    BOOLEAN("BOOLEAN", "布尔值");

    private final String code;
    private final String describe;

    IntentSlotValueType(String code, String describe) {
        this.code = code;
        this.describe = describe;
    }

    public String getCode() {
        return code;
    }

    public String getDescribe() {
        return describe;
    }
}

package com.lingxi.workbench.workflow.model.intent;

/**
 * 意图槽位支持的基础值类型。
 *
 * <p>V1 阶段不做强类型校验，仅作为前端表单控件的类型提示和澄清时的输入格式引导。
 * 后续可在运行时增加格式校验（如 DATE 类型验证日期格式）。</p>
 *
 * <ul>
 *   <li><b>STRING</b> — 文本类型，默认值类型</li>
 *   <li><b>NUMBER</b> — 数字类型</li>
 *   <li><b>DATE</b> — 日期类型</li>
 *   <li><b>BOOLEAN</b> — 布尔类型</li>
 * </ul>
 */
public enum IntentSlotValueType {

    /** 文本 */
    STRING("STRING", "文本"),

    /** 数字 */
    NUMBER("NUMBER", "数字"),

    /** 日期 */
    DATE("DATE", "日期"),

    /** 布尔值 */
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
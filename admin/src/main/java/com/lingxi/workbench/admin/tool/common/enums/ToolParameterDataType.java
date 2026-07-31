package com.lingxi.workbench.admin.tool.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/** Tool 参数支持的基础 JSON 数据类型。 */
@Getter
@RequiredArgsConstructor
public enum ToolParameterDataType {

    STRING("string", "字符串"),
    INTEGER("integer", "整数"),
    NUMBER("number", "数字"),
    BOOLEAN("boolean", "布尔值");

    /** 写入 JSON Schema property.type 的标准类型编码。 */
    private final String schemaType;

    /** 管理端展示名称。 */
    private final String describe;

    /** 根据受控 Schema 类型还原管理端参数类型。 */
    public static Optional<ToolParameterDataType> fromSchemaType(String schemaType) {
        return Arrays.stream(values()).filter(type -> type.schemaType.equals(schemaType)).findFirst();
    }
}

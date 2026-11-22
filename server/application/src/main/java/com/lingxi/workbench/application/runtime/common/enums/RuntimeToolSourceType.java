package com.lingxi.workbench.application.runtime.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * Application 运行端支持的 Tool 执行来源。
 */
@Getter
@RequiredArgsConstructor
public enum RuntimeToolSourceType {

    LOCAL_FUNCTION("LOCAL_FUNCTION", "本地函数"),
    MCP("MCP", "MCP 工具");

    @EnumValue
    private final String code;

    private final String describe;

    public static Optional<RuntimeToolSourceType> fromCode(String code) {
        return Arrays.stream(values()).filter(sourceType -> sourceType.code.equals(code)).findFirst();
    }
}

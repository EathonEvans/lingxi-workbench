package com.lingxi.workbench.application.runtime.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * MCP Server 运行端认证方式。
 */
@Getter
@RequiredArgsConstructor
public enum RuntimeMcpAuthenticationType {

    NONE("NONE", "无需认证"),
    BEARER_TOKEN("BEARER_TOKEN", "Bearer Token");

    @EnumValue
    private final String code;

    private final String describe;
}

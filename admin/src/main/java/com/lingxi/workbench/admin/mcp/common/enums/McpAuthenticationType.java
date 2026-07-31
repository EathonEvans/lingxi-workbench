package com.lingxi.workbench.admin.mcp.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * MCP Server 认证方式。
 */
@Getter
@RequiredArgsConstructor
public enum McpAuthenticationType {

    NONE("NONE", "无需认证"),

    BEARER_TOKEN("BEARER_TOKEN", "Bearer Token");

    @EnumValue
    private final String code;

    private final String describe;
}

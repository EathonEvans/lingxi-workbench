package com.lingxi.workbench.admin.mcp.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * MCP Server 最近一次连通性测试状态。
 */
@Getter
@RequiredArgsConstructor
public enum McpConnectionTestStatus {

    UNTESTED("UNTESTED", "未测试"),

    SUCCESS("SUCCESS", "测试成功"),

    FAILED("FAILED", "测试失败");

    @EnumValue
    private final String code;

    private final String describe;
}

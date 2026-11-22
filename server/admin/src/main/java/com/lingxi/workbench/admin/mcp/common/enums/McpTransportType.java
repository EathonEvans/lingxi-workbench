package com.lingxi.workbench.admin.mcp.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * MCP Server 传输协议。
 *
 * <p>第一阶段只开放 MCP 规范推荐的 Streamable HTTP。STDIO 需要管理子进程生命周期，
 * 不适合由当前多实例管理服务直接托管。</p>
 */
@Getter
@RequiredArgsConstructor
public enum McpTransportType {

    STREAMABLE_HTTP("STREAMABLE_HTTP", "Streamable HTTP");

    @EnumValue
    private final String code;

    private final String describe;
}

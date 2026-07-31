package com.lingxi.workbench.infra.ai.mcp;

/**
 * MCP 出站地址或连接参数不满足平台安全约束时抛出的异常。
 */
public class McpClientSecurityException extends RuntimeException {

    public McpClientSecurityException(String message) {
        super(message);
    }
}

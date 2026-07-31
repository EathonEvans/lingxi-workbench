package com.lingxi.workbench.mcp.tool.config;

import com.lingxi.workbench.infra.ai.tool.builtin.SystemCalculatorAgentTool;
import com.lingxi.workbench.infra.ai.tool.builtin.SystemCurrentTimeAgentTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 本地 MCP Server 的工具注册入口。
 *
 * <p>工具执行逻辑复用 infra-ai 中的平台 {@code AgentTool}，MCP 层只负责协议暴露。
 * Function Calling 与 MCP 因此共享相同的工具名称、描述、参数 Schema 和校验规则。</p>
 */
@Configuration(proxyBeanMethods = false)
public class LocalMcpToolConfiguration {

    @Bean
    public SystemCalculatorAgentTool systemCalculatorAgentTool() {
        return new SystemCalculatorAgentTool();
    }

    @Bean
    public SystemCurrentTimeAgentTool systemCurrentTimeAgentTool() {
        return new SystemCurrentTimeAgentTool();
    }

    /**
     * Spring AI MCP Starter 会把 Provider 中的回调转换为 MCP Tool Specification。
     */
    @Bean
    public ToolCallbackProvider localMcpToolCallbackProvider(SystemCalculatorAgentTool calculatorTool, SystemCurrentTimeAgentTool currentTimeTool) {
        return ToolCallbackProvider.from(calculatorTool.callback(), currentTimeTool.callback());
    }
}

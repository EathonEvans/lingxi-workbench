package com.lingxi.workbench.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Lingxi Workbench MCP 协议服务启动类。
 *
 * <p>本服务作为独立的 MCP（Model Context Protocol）协议端点，对外暴露统一的 Tool 能力。
 * 当前不直接访问数据库，首批只读工具复用 infra-ai 的稳定 Tool 契约与执行器。
 *
 * <p>启动时只扫描当前 MCP 服务包。
 * infra-ai 中的工具执行器由本模块显式注册，避免把模型网关等无关组件带入 MCP 进程。
 *
 * <p>MCP 服务与 admin、application 独立部署，不依赖对方的业务实现，
 * 后续业务工具通过稳定 Internal API 访问 application，不把业务模块直接打入本进程。
 *
 * @author lingxi
 * @since 1.0.0
 */
@SpringBootApplication
public class LingxiMcpServerApplication {

    /**
     * MCP 协议服务启动入口。
     *
     * <p>通过 {@link SpringApplication#run(Class, String...)} 启动 Spring Boot 应用上下文，
     * 加载所有被扫描到的 Bean 并完成 MCP 协议端点的注册与监听。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(LingxiMcpServerApplication.class, args);
    }
}

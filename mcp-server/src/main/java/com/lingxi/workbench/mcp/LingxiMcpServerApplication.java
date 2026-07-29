package com.lingxi.workbench.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * Lingxi Workbench MCP 协议服务启动类。
 *
 * <p>本服务作为独立的 MCP（Model Context Protocol）协议端点，对外暴露统一的 Tool 能力。
 * 当前不直接访问数据库，所有业务能力通过调用 application 模块的稳定内部接口接入，
 * 依赖方为 framework 和 infra-ai 公共契约层。
 *
 * <p>启动时排除 DataSource 自动配置，扫描 {@code com.lingxi.workbench} 包路径下的所有 Spring 组件。
 *
 * <p>MCP 服务与 admin、application 独立部署，不依赖对方的业务实现，
 * 仅通过共享基础契约（framework / infra-ai）和 Internal API 进行交互。
 *
 * @author lingxi
 * @since 1.0.0
 */
@SpringBootApplication(
        scanBasePackages = "com.lingxi.workbench",
        exclude = DataSourceAutoConfiguration.class)
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

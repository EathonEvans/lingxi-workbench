package com.lingxi.workbench.infra.ai.mcp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP 客户端出站连接安全配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "lingxi.mcp.security")
public class McpClientSecurityProperties {

    /**
     * 允许连接的私网或本机主机名。
     *
     * <p>公网地址无需配置；私网、回环和链路本地地址默认拒绝，
     * 防止动态 MCP 配置成为服务端请求伪造入口。</p>
     */
    private List<String> allowedPrivateHosts = new ArrayList<>();
}

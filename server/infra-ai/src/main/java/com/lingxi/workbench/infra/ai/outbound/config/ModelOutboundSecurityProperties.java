package com.lingxi.workbench.infra.ai.outbound.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型出站连接安全配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "lingxi.model.security")
public class ModelOutboundSecurityProperties {

    /**
     * 允许连接的私网或本机主机名。
     *
     * <p>公网地址无需配置；私网、回环和链路本地地址默认拒绝，
     * 防止动态模型配置成为服务端请求伪造入口。
     * 本地或内网部署模型（如 Ollama、vLLM）时需显式放行对应主机。</p>
     */
    private List<String> allowedPrivateHosts = new ArrayList<>();
}

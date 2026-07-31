package com.lingxi.workbench.infra.ai.mcp;

import com.lingxi.workbench.infra.ai.mcp.config.McpClientSecurityProperties;
import lombok.RequiredArgsConstructor;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MCP 客户端统一出站地址安全策略。
 *
 * <p>管理端保存配置和 Application 实际执行工具时都必须校验，
 * 避免配置保存后因 DNS 漂移或数据被篡改而绕过出站限制。</p>
 */
@RequiredArgsConstructor
public class McpServerAddressPolicy {

    private final McpClientSecurityProperties properties;

    public void validateConfiguration(String baseUrl, String endpoint) {
        URI uri = parseBaseUrl(baseUrl);
        if (Objects.nonNull(uri.getUserInfo()) || Objects.nonNull(uri.getQuery()) || Objects.nonNull(uri.getFragment())) {
            throw new McpClientSecurityException("MCP Server 地址不能包含用户信息、查询参数或片段");
        }
        String path = uri.getPath();
        if (Objects.nonNull(path) && !path.isBlank() && !"/".equals(path)) {
            throw new McpClientSecurityException("MCP Server 地址只填写协议、主机和端口，接口路径请填写在 Endpoint");
        }
        if (!endpoint.startsWith("/") || endpoint.contains("..") || endpoint.contains("?") || endpoint.contains("#")) {
            throw new McpClientSecurityException("MCP Endpoint 必须是以 / 开头的安全路径");
        }
    }

    public void validateOutboundAddress(String baseUrl) {
        URI uri = parseBaseUrl(baseUrl);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        Set<String> allowedPrivateHosts = properties.getAllowedPrivateHosts().stream().map(value -> value.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        if (allowedPrivateHosts.contains(host)) {
            return;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isPrivateAddress(address)) {
                    throw new McpClientSecurityException("MCP Server 指向私网地址，请先加入服务端白名单");
                }
            }
        } catch (UnknownHostException exception) {
            throw new McpClientSecurityException("无法解析 MCP Server 主机地址");
        }
    }

    private static URI parseBaseUrl(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl.trim());
            boolean supportedScheme = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            if (!supportedScheme || Objects.isNull(uri.getHost()) || uri.getHost().isBlank()) {
                throw new McpClientSecurityException("MCP Server 地址必须是有效的 HTTP 或 HTTPS 地址");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new McpClientSecurityException("MCP Server 地址格式不正确");
        }
    }

    private static boolean isPrivateAddress(InetAddress address) {
        return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress();
    }
}

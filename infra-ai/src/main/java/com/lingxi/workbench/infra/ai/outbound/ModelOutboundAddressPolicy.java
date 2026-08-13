package com.lingxi.workbench.infra.ai.outbound;

import com.lingxi.workbench.infra.ai.outbound.config.ModelOutboundSecurityProperties;
import lombok.RequiredArgsConstructor;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 模型与 Embedding 客户端统一出站地址安全策略。
 *
 * <p>管理端保存配置和运行端实际发起模型请求时都必须校验，
 * 避免配置保存后因 DNS 漂移或数据被篡改而绕过出站限制。</p>
 */
@RequiredArgsConstructor
public class ModelOutboundAddressPolicy {

    private final ModelOutboundSecurityProperties properties;

    /**
     * 校验出站地址，私网、回环和链路本地地址默认拒绝，白名单内主机放行。
     */
    public void validateOutboundAddress(String baseUrl) {
        URI uri = parseBaseUrl(baseUrl);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        Set<String> allowedPrivateHosts = properties.getAllowedPrivateHosts().stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        if (allowedPrivateHosts.contains(host)) {
            return;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isPrivateAddress(address)) {
                    throw new ModelOutboundSecurityException(
                            "模型地址指向私网或本机地址，如需访问请在服务端配置 lingxi.model.security.allowed-private-hosts 显式放行");
                }
            }
        } catch (UnknownHostException exception) {
            throw new ModelOutboundSecurityException("无法解析模型地址的主机名");
        }
    }

    private static URI parseBaseUrl(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl.trim());
            boolean supportedScheme = "http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme());
            if (!supportedScheme || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new ModelOutboundSecurityException("模型地址必须是有效的 HTTP 或 HTTPS 地址");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new ModelOutboundSecurityException("模型地址格式不正确");
        }
    }

    private static boolean isPrivateAddress(InetAddress address) {
        return address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress();
    }
}

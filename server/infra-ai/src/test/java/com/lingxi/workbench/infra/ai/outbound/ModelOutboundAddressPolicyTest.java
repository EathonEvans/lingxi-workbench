package com.lingxi.workbench.infra.ai.outbound;

import com.lingxi.workbench.infra.ai.outbound.config.ModelOutboundSecurityProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelOutboundAddressPolicyTest {

    @Test
    void shouldAllowWhitelistedHostWithoutResolving() {
        // 白名单命中时直接放行、不触发 DNS 解析，单测无需外部网络。
        assertDoesNotThrow(() -> policyWith(List.of("api.deepseek.com"))
                .validateOutboundAddress("https://api.deepseek.com/v1"));
    }

    @Test
    void shouldRejectLoopbackWithoutWhitelist() {
        ModelOutboundSecurityException exception = assertThrows(ModelOutboundSecurityException.class,
                () -> policyWith(List.of()).validateOutboundAddress("http://127.0.0.1:11434/v1"));

        assertEquals("模型地址指向私网或本机地址，如需访问请在服务端配置 lingxi.model.security.allowed-private-hosts 显式放行",
                exception.getMessage());
    }

    @Test
    void shouldRejectSiteLocalWithoutWhitelist() {
        assertThrows(ModelOutboundSecurityException.class,
                () -> policyWith(List.of()).validateOutboundAddress("http://10.0.0.5:8000/v1"));
        assertThrows(ModelOutboundSecurityException.class,
                () -> policyWith(List.of()).validateOutboundAddress("http://192.168.1.10:8000/v1"));
    }

    @Test
    void shouldAllowWhitelistedPrivateHost() {
        // 本地部署模型（如 Ollama、vLLM）通过白名单显式放行后校验通过。
        assertDoesNotThrow(() -> policyWith(List.of("127.0.0.1", "localhost"))
                .validateOutboundAddress("http://127.0.0.1:11434/v1"));
        assertDoesNotThrow(() -> policyWith(List.of("192.168.86.2"))
                .validateOutboundAddress("http://192.168.86.2:8000/v1"));
    }

    @Test
    void shouldRejectInvalidSchemeAndMalformedUrl() {
        assertThrows(ModelOutboundSecurityException.class,
                () -> policyWith(List.of()).validateOutboundAddress("ftp://example.com/v1"));
        assertThrows(ModelOutboundSecurityException.class,
                () -> policyWith(List.of()).validateOutboundAddress("not-a-url"));
    }

    private static ModelOutboundAddressPolicy policyWith(List<String> allowedPrivateHosts) {
        ModelOutboundSecurityProperties properties = new ModelOutboundSecurityProperties();
        properties.setAllowedPrivateHosts(allowedPrivateHosts);
        return new ModelOutboundAddressPolicy(properties);
    }
}

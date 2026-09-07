package com.lingxi.workbench.infra.ai.rerank.http;

import com.lingxi.workbench.infra.ai.outbound.ModelOutboundAddressPolicy;
import com.lingxi.workbench.infra.ai.outbound.config.ModelOutboundSecurityProperties;
import com.lingxi.workbench.infra.ai.rerank.config.RerankHttpProperties;
import com.lingxi.workbench.infra.ai.rerank.model.RerankModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.rerank.model.RerankResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/** 供应商边界采用真实 HTTP，验证协议、下标、分数和敏感数据隔离。 */
class HttpRerankGatewayTest {
    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @AfterEach
    void stop() { if (server != null) server.stop(0); }

    @Test
    void shouldSendBoundedDocumentsAndSortProviderScores() throws Exception {
        start(200, "{\"results\":[{\"index\":0,\"relevance_score\":0.4},{\"index\":1,\"relevance_score\":0.9}]}");
        assertThat(gateway().rerank("query", List.of("first", "second"), 2, config()))
                .containsExactly(new RerankResult(1, 0.9), new RerankResult(0, 0.4));
        assertThat(body.get()).contains("\"top_n\":2", "\"return_documents\":false", "\"documents\":[\"first\",\"second\"]");
        assertThat(authorization.get()).isEqualTo("Bearer test-secret");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"results\":[]}",
        "{\"results\":[{\"index\":2,\"relevance_score\":0.8}]}",
        "{\"results\":[{\"index\":-1,\"relevance_score\":0.8}]}",
        "{\"results\":[{\"index\":0.5,\"relevance_score\":0.8}]}",
        "{\"results\":[{\"index\":\"0\",\"relevance_score\":0.8}]}",
        "{\"results\":[{\"index\":0,\"relevance_score\":1.1}]}",
        "{\"results\":[{\"index\":0,\"relevance_score\":1e999}]}",
        "{\"results\":[{\"index\":0,\"relevance_score\":\"0.8\"}]}",
        "{\"results\":[{\"index\":0}]}", "private provider response"
    })
    void shouldRejectMalformedResultWithoutLeakingBody(String response) throws Exception {
        start(200, response);
        assertThatThrownBy(() -> gateway().rerank("query", List.of("private document"), 1, config()))
                .hasMessage("重排序服务返回无效的候选下标或相关分数");
    }

    @Test
    void shouldRejectDuplicateCandidateIndices() throws Exception {
        start(200, "{\"results\":[{\"index\":0,\"relevance_score\":0.8},{\"index\":0,\"relevance_score\":0.9}]}");
        assertThatThrownBy(() -> gateway().rerank("query", List.of("first", "second"), 2, config())).hasMessageContaining("无效");
    }

    @Test
    void shouldNotFollowRedirectOrExposeProviderError() throws Exception {
        start(302, "test-secret private document");
        assertThatThrownBy(() -> gateway().rerank("query", List.of("first"), 1, config()))
                .hasMessage("重排序服务返回 HTTP 302");
    }

    @Test
    void shouldNotCallProviderForEmptyCandidates() {
        assertThat(gateway().rerank("query", List.of(), 1, null)).isEmpty();
    }

    @Test
    void shouldRetryTransientErrorsOnlyWithinConfiguredLimit() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/rerank", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        assertThatThrownBy(() -> gateway().rerank("query", List.of("first"), 1, config().toBuilder().maxRetries(2).build()))
                .hasMessage("重排序服务返回 HTTP 503");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void shouldNotRetryInvalidCredentials() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/rerank", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();
        assertThatThrownBy(() -> gateway().rerank("query", List.of("first"), 1, config().toBuilder().maxRetries(2).build()))
                .hasMessage("重排序服务返回 HTTP 401");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void shouldRejectOversizedRequestBeforeSendingAnyDocument() throws Exception {
        start(200, "{}");
        RerankHttpProperties limits = new RerankHttpProperties();
        limits.setMaxRequestBytes(100);
        assertThatThrownBy(() -> gateway(limits).rerank("query", List.of("private".repeat(100)), 1, config()))
                .hasMessage("重排序请求正文超过平台字节上限");
        assertThat(body.get()).isNull();
    }

    @Test
    void shouldCancelOversizedResponseWithoutRetrying() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/rerank", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write("private".repeat(500).getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        RerankHttpProperties limits = new RerankHttpProperties();
        limits.setMaxResponseBytes(100);
        assertThatThrownBy(() -> gateway(limits).rerank("query", List.of("text"), 1, config().toBuilder().maxRetries(2).build()))
                .hasMessage("重排序响应超过平台字节上限");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void shouldApplyDeadlineWhileResponseBodyIsStillStreaming() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/rerank", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write("{".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try { Thread.sleep(1800); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        long started = System.nanoTime();
        assertThatThrownBy(() -> gateway().rerank("query", List.of("text"), 1, config().toBuilder().timeoutSeconds(1).build()))
                .hasMessageContaining("超时");
        assertThat(java.time.Duration.ofNanos(System.nanoTime() - started).toMillis()).isLessThan(1700);
    }

    private void start(int status, String response) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/rerank", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().set("Location", "/redirect");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    private RerankModelRuntimeConfig config() {
        return RerankModelRuntimeConfig.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/")
                .apiKey("test-secret").modelCode("reranker").timeoutSeconds(3).maxRetries(0).build();
    }

    private static HttpRerankGateway gateway() { return gateway(new RerankHttpProperties()); }

    private static HttpRerankGateway gateway(RerankHttpProperties limits) {
        ModelOutboundSecurityProperties properties = new ModelOutboundSecurityProperties();
        properties.setAllowedPrivateHosts(List.of("127.0.0.1"));
        return new HttpRerankGateway(new ModelOutboundAddressPolicy(properties), limits);
    }
}

package com.lingxi.workbench.admin.model.service.impl;

import com.lingxi.workbench.admin.model.model.bo.ModelConnectionTestResult;
import com.lingxi.workbench.admin.model.common.enums.ModelType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpModelConnectionTesterTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldCallConfiguredChatEndpointWithCredentialAndModel() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        HttpModelConnectionTester tester = new HttpModelConnectionTester();
        ModelConnectionTestResult result = tester.test(
                "OPENAI_COMPATIBLE", baseUrl(), "secret-key", "gpt-test", ModelType.CHAT, 3);

        assertEquals("SUCCESS", result.status().name());
        assertEquals("Bearer secret-key", authorization.get());
        assertTrue(requestBody.get().contains("\"model\":\"gpt-test\""));
    }

    @Test
    void shouldReturnSanitizedProviderError() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] response = "{\"error\":{\"message\":\"API Key 无效\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        ModelConnectionTestResult result = new HttpModelConnectionTester().test(
                "OPENAI_COMPATIBLE", baseUrl(), "wrong-key", "gpt-test", ModelType.CHAT, 3);

        assertEquals("FAILED", result.status().name());
        assertEquals("HTTP_401", result.errorCode());
        assertEquals("API Key 无效", result.message());
    }

    @Test
    void shouldRejectUnsupportedProtocolWithoutSendingRequest() {
        ModelConnectionTestResult result = new HttpModelConnectionTester().test(
                "ANTHROPIC", "https://example.com/v1", "secret", "claude", ModelType.CHAT, 3);

        assertEquals("FAILED", result.status().name());
        assertEquals("UNSUPPORTED_PROTOCOL", result.errorCode());
    }

    @Test
    void shouldCallConfiguredEmbeddingEndpoint() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"data\":[{\"embedding\":[0.1]}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        ModelConnectionTestResult result = new HttpModelConnectionTester().test(
                "OPENAI_COMPATIBLE", baseUrl(), "secret", "embedding-test", ModelType.EMBEDDING, 3);

        assertEquals("SUCCESS", result.status().name());
        assertTrue(requestBody.get().contains("\"input\":\"连通性测试\""));
    }

    @Test
    void shouldRejectRerankUntilProviderProtocolIsAvailable() {
        ModelConnectionTestResult result = new HttpModelConnectionTester().test(
                "OPENAI_COMPATIBLE", "https://example.com/v1", "secret", "rerank-test", ModelType.RERANK, 3);

        assertEquals("FAILED", result.status().name());
        assertEquals("UNSUPPORTED_MODEL_TYPE", result.errorCode());
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }
}

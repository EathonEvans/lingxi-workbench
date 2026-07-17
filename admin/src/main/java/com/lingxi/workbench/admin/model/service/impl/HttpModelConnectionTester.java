package com.lingxi.workbench.admin.model.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.admin.model.model.bo.ModelConnectionTestResult;
import com.lingxi.workbench.admin.model.common.enums.ModelType;
import com.lingxi.workbench.admin.model.service.ModelConnectionTester;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** 使用 JDK HTTP 客户端调用供应商真实接口。 */
@Component
public class HttpModelConnectionTester implements ModelConnectionTester {

    private static final String OPENAI_COMPATIBLE = "OPENAI_COMPATIBLE";
    private static final int MAX_MESSAGE_LENGTH = 255;

    private final HttpClient httpClient;

    public HttpModelConnectionTester() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    /** 包级构造器仅供测试注入可控 HTTP 客户端。 */
    HttpModelConnectionTester(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public ModelConnectionTestResult test(
            String protocol,
            String baseUrl,
            String apiKey,
            String modelCode,
            ModelType modelType,
            int timeoutSeconds) {
        if (!OPENAI_COMPATIBLE.equals(protocol)) {
            return ModelConnectionTestResult.failed(
                    0, "UNSUPPORTED_PROTOCOL", "暂不支持该供应商协议的连通性测试");
        }
        if (modelType == ModelType.RERANK) {
            return ModelConnectionTestResult.failed(
                    0, "UNSUPPORTED_MODEL_TYPE", "Rerank 模型需要按供应商协议适配连通性测试");
        }
        return testOpenAiCompatible(baseUrl, apiKey, modelCode, modelType, timeoutSeconds);
    }

    private ModelConnectionTestResult testOpenAiCompatible(
            String baseUrl,
            String apiKey,
            String modelCode,
            ModelType modelType,
            int timeoutSeconds) {
        long startedAt = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(buildModelUri(baseUrl, modelType))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(modelCode, modelType)))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int latencyMs = elapsedMillis(startedAt);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return ModelConnectionTestResult.success(latencyMs);
            }
            return ModelConnectionTestResult.failed(
                    latencyMs,
                    "HTTP_" + response.statusCode(),
                    extractErrorMessage(response.body(), response.statusCode()));
        } catch (HttpTimeoutException exception) {
            return ModelConnectionTestResult.failed(
                    elapsedMillis(startedAt), "TIMEOUT", "请求供应商接口超时");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ModelConnectionTestResult.failed(
                    elapsedMillis(startedAt), "INTERRUPTED", "连通性测试被中断");
        } catch (IllegalArgumentException exception) {
            return ModelConnectionTestResult.failed(
                    elapsedMillis(startedAt), "INVALID_BASE_URL", "供应商接口地址不合法");
        } catch (IOException exception) {
            return ModelConnectionTestResult.failed(
                    elapsedMillis(startedAt), "CONNECTION_ERROR", "无法连接供应商接口");
        }
    }

    private static URI buildModelUri(String baseUrl, ModelType modelType) {
        String normalized = CharSequenceUtil.isBlank(baseUrl) ? "" : baseUrl.trim().replaceAll("/+$", "");
        String endpoint = modelType == ModelType.EMBEDDING ? "/embeddings" : "/chat/completions";
        URI uri = URI.create(normalized + endpoint);
        if (!("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("仅支持 HTTP 或 HTTPS 地址");
        }
        return uri;
    }

    private static String buildRequestBody(String modelCode, ModelType modelType) {
        if (modelType == ModelType.EMBEDDING) {
            return JSON.toJSONString(Map.of("model", modelCode, "input", "连通性测试"));
        }
        return JSON.toJSONString(Map.of(
                "model", modelCode,
                "messages", List.of(Map.of("role", "user", "content", "请回复 OK")),
                "max_tokens", 1,
                "temperature", 0,
                "stream", false));
    }

    private static String extractErrorMessage(String responseBody, int statusCode) {
        try {
            JSONObject root = JSON.parseObject(responseBody);
            JSONObject error = root == null ? null : root.getJSONObject("error");
            String message = error == null ? null : error.getString("message");
            if (CharSequenceUtil.isNotBlank(message)) {
                return truncate(message.trim());
            }
        } catch (RuntimeException ignored) {
            // 第三方接口可能返回 HTML 或纯文本，禁止将不可控响应内容直接透传给前端。
        }
        return "供应商接口返回 HTTP " + statusCode;
    }

    private static int elapsedMillis(long startedAt) {
        long elapsed = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        return (int) Math.min(elapsed, Integer.MAX_VALUE);
    }

    private static String truncate(String value) {
        return value.length() <= MAX_MESSAGE_LENGTH
                ? value
                : value.substring(0, MAX_MESSAGE_LENGTH);
    }
}

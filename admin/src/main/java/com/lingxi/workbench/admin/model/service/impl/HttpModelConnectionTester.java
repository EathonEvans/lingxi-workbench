package com.lingxi.workbench.admin.model.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.admin.model.model.bo.ModelConnectionTestResult;
import com.lingxi.workbench.admin.model.common.enums.ModelType;
import com.lingxi.workbench.admin.model.service.ModelConnectionTester;
import com.lingxi.workbench.infra.ai.outbound.ModelOutboundAddressPolicy;
import com.lingxi.workbench.infra.ai.outbound.ModelOutboundSecurityException;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ModelOutboundAddressPolicy outboundAddressPolicy;

    @Autowired
    public HttpModelConnectionTester(ModelOutboundAddressPolicy outboundAddressPolicy) {
        this(defaultHttpClient(), outboundAddressPolicy);
    }

    /** 包级构造器仅供测试注入可控 HTTP 客户端。 */
    HttpModelConnectionTester(HttpClient httpClient, ModelOutboundAddressPolicy outboundAddressPolicy) {
        this.httpClient = httpClient;
        this.outboundAddressPolicy = outboundAddressPolicy;
    }

    private static HttpClient defaultHttpClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public ModelConnectionTestResult test(
            String protocol,
            String baseUrl,
            String apiKey,
            String modelCode,
            ModelType modelType,
            JSONObject extensionParameters,
            int timeoutSeconds) {
        if (!OPENAI_COMPATIBLE.equals(protocol)) {
            return ModelConnectionTestResult.failed(
                    0, "UNSUPPORTED_PROTOCOL", "暂不支持该供应商协议的连通性测试");
        }
        if (modelType == ModelType.RERANK) {
            return ModelConnectionTestResult.failed(
                    0, "UNSUPPORTED_MODEL_TYPE", "Rerank 模型需要按供应商协议适配连通性测试");
        }
        return testOpenAiCompatible(
                baseUrl,
                apiKey,
                modelCode,
                modelType,
                extensionParameters,
                timeoutSeconds);
    }

    /** 按 OpenAI 兼容协议发起模型接口测试请求，构造最小请求体以验证连通性。 */
    private ModelConnectionTestResult testOpenAiCompatible(
            String baseUrl,
            String apiKey,
            String modelCode,
            ModelType modelType,
            JSONObject extensionParameters,
            int timeoutSeconds) {
        long startedAt = System.nanoTime();
        try {
            // 出站地址安全校验：私网、回环和链路本地地址默认拒绝，需在服务端配置显式放行。
            outboundAddressPolicy.validateOutboundAddress(baseUrl);
        } catch (ModelOutboundSecurityException exception) {
            return ModelConnectionTestResult.failed(0, "INVALID_BASE_URL", exception.getMessage());
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(buildModelUri(baseUrl, modelType))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(
                            modelCode,
                            modelType,
                            extensionParameters)))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int latencyMs = elapsedMillis(startedAt);
            // 2xx HTTP 状态码视为连接成功
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

    /** 根据供应商 baseUrl 和模型类型构造真实 API 端点地址，Chat 走 /chat/completions，Embedding 走 /embeddings。 */
    private static URI buildModelUri(String baseUrl, ModelType modelType) {
        String normalized = CharSequenceUtil.isBlank(baseUrl) ? "" : baseUrl.trim().replaceAll("/+$", "");
        String endpoint = modelType == ModelType.EMBEDDING ? "/embeddings" : "/chat/completions";
        URI uri = URI.create(normalized + endpoint);
        // 仅允许 HTTP/HTTPS 协议
        if (!("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("仅支持 HTTP 或 HTTPS 地址");
        }
        return uri;
    }

    /** 构造连通性测试用的最小请求体。Embedding 模型仅发送模型标识；Chat 模型发送最小 tokens 的对话。 */
    private static String buildRequestBody(
            String modelCode,
            ModelType modelType,
            JSONObject extensionParameters) {
        JSONObject body = new JSONObject();
        if (extensionParameters != null) {
            body = JSON.parseObject(JSON.toJSONString(extensionParameters));
        }
        // 核心字段最后写入，即使校验器被绕过，也不能被扩展参数覆盖。
        if (modelType == ModelType.EMBEDDING) {
            body.put("model", modelCode);
            body.put("input", "连通性测试");
            return JSON.toJSONString(body);
        }
        body.put("model", modelCode);
        body.put(
                "messages",
                List.of(Map.of("role", "user", "content", "请回复 OK")));
        body.put("max_tokens", 1);
        body.put("temperature", 0);
        body.put("stream", false);
        return JSON.toJSONString(body);
    }

    /** 从供应商 JSON 响应体中提取错误信息，仅截取前 255 个字符避免过长消息。 */
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

    /** 计算自 startedAt 以来的耗时毫秒数。 */
    private static int elapsedMillis(long startedAt) {
        long elapsed = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        return (int) Math.min(elapsed, Integer.MAX_VALUE);
    }

    /** 截断消息至最大长度，避免接口返回异常长文本。 */
    private static String truncate(String value) {
        return value.length() <= MAX_MESSAGE_LENGTH
                ? value
                : value.substring(0, MAX_MESSAGE_LENGTH);
    }
}

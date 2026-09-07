package com.lingxi.workbench.infra.ai.rerank.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.infra.ai.outbound.ModelOutboundAddressPolicy;
import com.lingxi.workbench.infra.ai.rerank.RerankGateway;
import com.lingxi.workbench.infra.ai.rerank.config.RerankHttpProperties;
import com.lingxi.workbench.infra.ai.rerank.model.RerankModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.rerank.model.RerankResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Jina / SiliconFlow 风格的 /rerank 协议；baseUrl 包含服务版本前缀。 */
@Slf4j
@Component
@EnableConfigurationProperties(RerankHttpProperties.class)
public class HttpRerankGateway implements RerankGateway {
    private final ModelOutboundAddressPolicy addressPolicy;
    private final RerankHttpProperties properties;
    // 禁止自动跳转，避免已校验地址将知识正文或密钥转发到其他主机。
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build();

    public HttpRerankGateway(ModelOutboundAddressPolicy addressPolicy, RerankHttpProperties properties) {
        this.addressPolicy = addressPolicy;
        this.properties = properties;
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int topN, RerankModelRuntimeConfig config) {
        if (documents.isEmpty()) {
            return List.of();
        }
        addressPolicy.validateOutboundAddress(config.getBaseUrl());
        int count = Math.min(topN, documents.size());
        JSONObject body = JSONObject.of("model", config.getModelCode(), "query", query,
                "documents", documents, "top_n", count, "return_documents", false);
        byte[] requestBody = JSON.toJSONString(body).getBytes(StandardCharsets.UTF_8);
        if (requestBody.length > properties.getMaxRequestBytes()) {
            throw failure("重排序请求正文超过平台字节上限");
        }
        long deadline = System.nanoTime() + Duration.ofSeconds(config.getTimeoutSeconds()).toNanos();
        for (int attempt = 0; ; attempt++) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                throw failure("重排序服务请求超时");
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.getBaseUrl().replaceAll("/+$", "") + "/rerank"))
                    .timeout(Duration.ofNanos(remainingNanos))
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody)).build();
            AtomicBoolean responseExceeded = new AtomicBoolean();
            CompletableFuture<HttpResponse<byte[]>> pending = httpClient.sendAsync(request,
                    ignored -> limitedSubscriber(properties.getMaxResponseBytes(), responseExceeded));
            try {
                // HttpRequest.timeout 不覆盖收到响应头后的慢正文；等待完整 BodySubscriber 完成才算请求结束。
                HttpResponse<byte[]> response = pending.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                int status = response.statusCode();
                if (status >= 500 && attempt < config.getMaxRetries()) {
                    log.warn("重排序供应商暂时失败，modelInstanceId={}，retry={}，httpStatus={}", config.getInstanceId(), attempt + 1, status);
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw failure("重排序服务返回 HTTP " + status);
                }
                return parseResults(new String(response.body(), StandardCharsets.UTF_8), documents.size(), count);
            } catch (TimeoutException exception) {
                pending.cancel(true);
                throw failure("重排序服务请求超时");
            } catch (InterruptedException exception) {
                pending.cancel(true);
                Thread.currentThread().interrupt();
                throw failure("重排序请求被中断");
            } catch (ExecutionException exception) {
                if (responseExceeded.get()) {
                    throw failure("重排序响应超过平台字节上限");
                }
                if (!(exception.getCause() instanceof IOException) || attempt >= config.getMaxRetries()) {
                    // 不回传供应商正文、查询、文档或密钥；失败不降级为向量结果。
                    throw failure("重排序服务请求失败或超时");
                }
                log.warn("重排序连接暂时失败，modelInstanceId={}，retry={}", config.getInstanceId(), attempt + 1);
            }
        }
    }

    /** 在读取响应过程中取消超限流，避免 ofString 先将不受控正文完整加载进内存。 */
    private static HttpResponse.BodySubscriber<byte[]> limitedSubscriber(int limit, AtomicBoolean exceeded) {
        return new HttpResponse.BodySubscriber<>() {
            private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
            private Flow.Subscription subscription;
            private long received;

            @Override
            public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
            @Override
            public void onSubscribe(Flow.Subscription upstream) {
                subscription = upstream;
                delegate.onSubscribe(upstream);
            }
            @Override
            public void onNext(List<ByteBuffer> buffers) {
                for (ByteBuffer buffer : buffers) received += buffer.remaining();
                if (received > limit) {
                    exceeded.set(true);
                    subscription.cancel();
                    delegate.onError(new IOException("rerank response limit exceeded"));
                    return;
                }
                delegate.onNext(buffers);
            }
            @Override
            public void onError(Throwable error) { delegate.onError(error); }
            @Override
            public void onComplete() { delegate.onComplete(); }
        };
    }

    private static List<RerankResult> parseResults(String body, int documentCount, int expectedCount) {
        try {
            JsonArray items = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("results");
            if (items.size() != expectedCount) {
                throw new IllegalArgumentException("count");
            }
            List<RerankResult> results = new ArrayList<>();
            Set<Integer> indices = new HashSet<>();
            for (JsonElement item : items) {
                JsonObject value = item.getAsJsonObject();
                JsonElement rawIndex = value.get("index");
                JsonElement rawScore = value.get("relevance_score");
                if (!rawIndex.getAsJsonPrimitive().isNumber() || !rawScore.getAsJsonPrimitive().isNumber()) {
                    throw new IllegalArgumentException("number");
                }
                int index = rawIndex.getAsBigDecimal().intValueExact();
                double score = rawScore.getAsDouble();
                if (index < 0 || index >= documentCount || !indices.add(index)
                        || !Double.isFinite(score) || score < 0 || score > 1) {
                    throw new IllegalArgumentException("range");
                }
                results.add(new RerankResult(index, score));
            }
            return results.stream().sorted(Comparator.comparingDouble(RerankResult::score).reversed()
                    .thenComparingInt(RerankResult::index)).toList();
        } catch (RuntimeException exception) {
            throw failure("重排序服务返回无效的候选下标或相关分数");
        }
    }

    private static BusinessException failure(String message) {
        return new BusinessException(HttpStatusEnum.SERVICE_UNAVAILABLE, message);
    }
}

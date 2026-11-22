package com.lingxi.workbench.application.runtime.evaluation;

import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** V2 离线评测通过内部接口逐条调用生产版本决策链，不装配第二套模型运行时。 */
public class IntentRoutingVersionPreviewGateway implements IntentRoutingEvaluationPreview {

    private static final String OPS_TOKEN_HEADER = "X-Lingxi-Ops-Token";

    private final IntentEvalGatewayProperties properties;
    private final HttpClient httpClient;

    public IntentRoutingVersionPreviewGateway(IntentEvalGatewayProperties properties) {
        this(properties, HttpClient.newBuilder().connectTimeout(properties.getTimeout()).build());
    }

    IntentRoutingVersionPreviewGateway(
            IntentEvalGatewayProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    public IntentRoutingPreviewResult preview(Long applicationId, Long applicationVersionId, String query) {
        requireConfigured();
        try {
            IntentRoutingVersionPreviewRequest body = new IntentRoutingVersionPreviewRequest(
                    applicationId, applicationVersionId, query);
            HttpRequest request = HttpRequest.newBuilder(uri("/internal/v1/intent-routing/preview-version"))
                    .timeout(properties.getTimeout())
                    .header(OPS_TOKEN_HEADER, properties.getToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return requireResult(response);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatusEnum.SERVICE_UNAVAILABLE, "V2意图评测被中断");
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(HttpStatusEnum.SERVICE_UNAVAILABLE, "V2意图评测接口不可用");
        }
    }

    private static IntentRoutingPreviewResult requireResult(HttpResponse<String> response) {
        JSONObject envelope = JSON.parseObject(response.body());
        if (response.statusCode() != HttpStatusEnum.OK.getCode()
                || envelope == null
                || envelope.getIntValue("code") != HttpStatusEnum.OK.getCode()) {
            String message = envelope == null ? null : envelope.getString("msg");
            throw new BusinessException(status(response.statusCode()),
                    message == null || message.isBlank() ? "V2意图评测失败" : message);
        }
        JSONObject data = envelope.getJSONObject("data");
        if (data == null) {
            throw new BusinessException(HttpStatusEnum.SERVICE_UNAVAILABLE, "V2意图评测响应缺少data");
        }
        return data.toJavaObject(IntentRoutingPreviewResult.class);
    }

    private void requireConfigured() {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
                || properties.getToken() == null || properties.getToken().isBlank()) {
            throw new BusinessException(HttpStatusEnum.SERVICE_UNAVAILABLE,
                    "V2意图评测必须配置Application地址和内部令牌");
        }
    }

    private URI uri(String path) {
        String baseUrl = properties.getBaseUrl().endsWith("/")
                ? properties.getBaseUrl().substring(0, properties.getBaseUrl().length() - 1)
                : properties.getBaseUrl();
        return URI.create(baseUrl + path);
    }

    private static HttpStatusEnum status(int statusCode) {
        return switch (statusCode) {
            case 400 -> HttpStatusEnum.BAD_REQUEST;
            case 404 -> HttpStatusEnum.NOT_FOUND;
            case 409 -> HttpStatusEnum.CONFLICT;
            default -> HttpStatusEnum.SERVICE_UNAVAILABLE;
        };
    }
}

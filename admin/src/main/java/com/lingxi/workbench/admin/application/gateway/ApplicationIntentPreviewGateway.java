package com.lingxi.workbench.admin.application.gateway;

import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.admin.application.config.ApplicationIntentPreviewProperties;
import com.lingxi.workbench.admin.application.model.vo.ApplicationIntentPreviewVO;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Admin 到 Application 意图草稿测试接口的安全代理，不向浏览器暴露内部令牌。 */
@Slf4j
public class ApplicationIntentPreviewGateway {

    private static final String OPS_TOKEN_HEADER = "X-Lingxi-Ops-Token";

    private final ApplicationIntentPreviewProperties properties;
    private final HttpClient httpClient;

    public ApplicationIntentPreviewGateway(ApplicationIntentPreviewProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public ApplicationIntentPreviewVO preview(
            Long applicationId, String applicationCode, String query, ApplicationDefinition definition) {
        requireConfigured();
        try {
            IntentPreviewGatewayRequest body = new IntentPreviewGatewayRequest(
                    applicationId, applicationCode, query, definition);
            HttpRequest request = HttpRequest.newBuilder(uri("/internal/v1/intent-routing/preview"))
                    .timeout(properties.getTimeout())
                    .header(OPS_TOKEN_HEADER, properties.getToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return requireResult(response);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            log.warn("Application意图草稿测试不可用，errorType={}", exception.getClass().getSimpleName());
            throw unavailable();
        }
    }

    private ApplicationIntentPreviewVO requireResult(HttpResponse<String> response) {
        JSONObject envelope = JSON.parseObject(response.body());
        if (response.statusCode() != HttpStatusEnum.OK.getCode()
                || envelope == null
                || envelope.getIntValue("code") != HttpStatusEnum.OK.getCode()) {
            String message = envelope == null ? null : envelope.getString("msg");
            throw new BusinessException(status(response.statusCode()),
                    message == null || message.isBlank() ? "Application意图草稿测试失败" : message);
        }
        JSONObject data = envelope.getJSONObject("data");
        if (data == null) {
            throw unavailable();
        }
        return data.toJavaObject(ApplicationIntentPreviewVO.class);
    }

    private URI uri(String path) {
        String baseUrl = properties.getBaseUrl().endsWith("/")
                ? properties.getBaseUrl().substring(0, properties.getBaseUrl().length() - 1)
                : properties.getBaseUrl();
        return URI.create(baseUrl + path);
    }

    private void requireConfigured() {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
                || properties.getToken() == null || properties.getToken().isBlank()) {
            throw unavailable();
        }
    }

    private static HttpStatusEnum status(int statusCode) {
        return switch (statusCode) {
            case 400 -> HttpStatusEnum.BAD_REQUEST;
            case 404 -> HttpStatusEnum.NOT_FOUND;
            case 409 -> HttpStatusEnum.CONFLICT;
            default -> HttpStatusEnum.SERVICE_UNAVAILABLE;
        };
    }

    private static BusinessException unavailable() {
        return new BusinessException(HttpStatusEnum.SERVICE_UNAVAILABLE, "Application意图草稿测试暂不可用");
    }

    /**
     * @param applicationId   Application主键
     * @param applicationCode Application稳定编码
     * @param query           测试问题
     * @param definition      已由Admin解析并锁定的定义
     */
    private record IntentPreviewGatewayRequest(
            Long applicationId, String applicationCode, String query, ApplicationDefinition definition) {
    }
}

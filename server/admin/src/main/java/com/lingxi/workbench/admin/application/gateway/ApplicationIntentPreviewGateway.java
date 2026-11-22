package com.lingxi.workbench.admin.application.gateway;

import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.admin.application.config.AppIntentPreviewProperties;
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
import java.util.List;

/** Admin 到 Application 意图草稿测试接口的安全代理，不向浏览器暴露内部令牌。 */
@Slf4j
public class ApplicationIntentPreviewGateway {

    private static final String OPS_TOKEN_HEADER = "X-Lingxi-Ops-Token";

    private final AppIntentPreviewProperties properties;
    private final HttpClient httpClient;

    public ApplicationIntentPreviewGateway(AppIntentPreviewProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public ApplicationIntentPreviewVO preview(
            Long applicationId, String applicationCode, String query, ApplicationDefinition definition) {
        IntentPreviewGatewayRequest body = new IntentPreviewGatewayRequest(
                applicationId, applicationCode, query, definition);
        return post("/internal/v1/intent-routing/preview", body)
                .toJavaObject(ApplicationIntentPreviewVO.class);
    }

    /** 使用锁定的正式版本执行一次意图路由评测。 */
    public ApplicationIntentPreviewVO previewVersion(
            Long applicationId, Long applicationVersionId, String query) {
        IntentVersionPreviewGatewayRequest body = new IntentVersionPreviewGatewayRequest(
                applicationId, applicationVersionId, query);
        return post("/internal/v1/intent-routing/preview-version", body)
                .toJavaObject(ApplicationIntentPreviewVO.class);
    }

    /** 获取由 Application 校验并补齐版本锁定信息的评测模板。 */
    public JSONObject evaluationTemplate(
            Long applicationId, Long applicationVersionId, String datasetVersion) {
        IntentEvaluationTemplateGatewayRequest body = new IntentEvaluationTemplateGatewayRequest(
                applicationId, applicationVersionId, datasetVersion);
        return post("/internal/v1/intent-routing/evaluation-template", body);
    }

    /** 将逐条决策结果交回 Application 生成统一生产门禁报告。 */
    public JSONObject evaluationReport(JSONObject dataset, List<JSONObject> results) {
        return post("/internal/v1/intent-routing/evaluation-report",
                new IntentEvaluationReportGatewayRequest(dataset, results));
    }

    /** 在执行模型前由 Application 校验样本覆盖和版本锁定契约。 */
    public void validateEvaluation(JSONObject dataset) {
        post("/internal/v1/intent-routing/evaluation-validate", dataset);
    }

    private JSONObject post(String path, Object body) {
        requireConfigured();
        try {
            HttpRequest request = HttpRequest.newBuilder(uri(path))
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
            log.warn("Application意图路由服务不可用，errorType={}", exception.getClass().getSimpleName());
            throw unavailable();
        }
    }

    private JSONObject requireResult(HttpResponse<String> response) {
        JSONObject envelope = JSON.parseObject(response.body());
        if (response.statusCode() != HttpStatusEnum.OK.getCode()
                || envelope == null
                || envelope.getIntValue("code") != HttpStatusEnum.OK.getCode()) {
            String message = envelope == null ? null : envelope.getString("msg");
            throw new BusinessException(status(response.statusCode()),
                    message == null || message.isBlank() ? "Application意图路由调用失败" : message);
        }
        JSONObject data = envelope.getJSONObject("data");
        if (data == null) {
            throw unavailable();
        }
        return data;
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
        return new BusinessException(HttpStatusEnum.SERVICE_UNAVAILABLE, "Application意图路由服务暂不可用");
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

    /**
     * @param applicationId        Application主键
     * @param applicationVersionId ApplicationVersion主键
     * @param query                评测问题
     */
    private record IntentVersionPreviewGatewayRequest(
            Long applicationId, Long applicationVersionId, String query) {
    }

    /**
     * @param applicationId        Application主键
     * @param applicationVersionId ApplicationVersion主键
     * @param datasetVersion       Langfuse数据集快照时刻
     */
    private record IntentEvaluationTemplateGatewayRequest(
            Long applicationId, Long applicationVersionId, String datasetVersion) {
    }

    /**
     * @param dataset 锁定版本和用例快照
     * @param results 逐条生产路由决策结果
     */
    private record IntentEvaluationReportGatewayRequest(
            JSONObject dataset, List<JSONObject> results) {
    }
}

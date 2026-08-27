package com.lingxi.workbench.admin.appmonitor.gateway;

import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.framework.json.JSONArray;
import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.admin.appmonitor.config.ConversationOpsGatewayProperties;
import com.lingxi.workbench.admin.appmonitor.model.request.ReplayConversationDlqRequest;
import com.lingxi.workbench.admin.appmonitor.model.vo.ConversationDlqMessageVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.ConversationDlqResultVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.ConversationOpsOverviewVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.ConversationReconcileVO;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Admin到Application会话运维接口的安全代理，不向浏览器暴露内部地址和令牌。 */
@Slf4j
public class ConversationOpsGateway {

    private static final String OPS_TOKEN_HEADER = "X-Lingxi-Ops-Token";

    private final ConversationOpsGatewayProperties properties;
    private final HttpClient httpClient;

    public ConversationOpsGateway(ConversationOpsGatewayProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public ConversationOpsOverviewVO overview() {
        if (!isConfigured()) {
            return unavailableOverview("会话运维内部接口尚未配置");
        }
        try {
            JSONObject data = requireData(send(get("/internal/v1/conversation-ops/overview")));
            ConversationOpsOverviewVO overview = data.toJavaObject(ConversationOpsOverviewVO.class);
            overview.setSourceAvailable(true);
            overview.setSourceMessage("会话异步链路数据已同步");
            return overview;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return unavailableOverview("会话运维查询被中断");
        } catch (IOException | RuntimeException exception) {
            log.warn("Application会话运维总览不可用，errorType={}", exception.getClass().getSimpleName());
            return unavailableOverview("会话运维数据暂不可用");
        }
    }

    public ConversationDlqResultVO dlq(int limit) {
        if (!isConfigured()) {
            return new ConversationDlqResultVO(false, "会话运维内部接口尚未配置", List.of());
        }
        try {
            JSONArray data = requireArray(send(get("/internal/v1/conversation-ops/dlq?limit=" + limit)));
            List<ConversationDlqMessageVO> records = data.toJavaList(ConversationDlqMessageVO.class);
            return new ConversationDlqResultVO(true, "死信数据已同步", records);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ConversationDlqResultVO(false, "死信查询被中断", List.of());
        } catch (IOException | RuntimeException exception) {
            log.warn("Application会话死信查询不可用，errorType={}", exception.getClass().getSimpleName());
            return new ConversationDlqResultVO(false, "会话运维数据暂不可用", List.of());
        }
    }

    public ConversationReconcileVO reconcile() {
        JSONObject data = requireMutationData("/internal/v1/conversation-ops/reconcile", new JSONObject());
        return data.toJavaObject(ConversationReconcileVO.class);
    }

    public void replay(ReplayConversationDlqRequest request) {
        JSONObject body = new JSONObject();
        body.put("group", request.group());
        body.put("msgId", request.msgId());
        requireMutation("/internal/v1/conversation-ops/dlq/replay", body);
    }

    private JSONObject requireMutationData(String path, JSONObject body) {
        JSONObject envelope = requireMutation(path, body);
        JSONObject data = envelope.getJSONObject("data");
        if (data == null) {
            throw unavailable();
        }
        return data;
    }

    private JSONObject requireMutation(String path, JSONObject body) {
        if (!isConfigured()) {
            throw unavailable();
        }
        try {
            return send(post(path, body));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException | RuntimeException exception) {
            log.warn("Application会话运维操作失败，path={}，errorType={}", path,
                    exception.getClass().getSimpleName());
            throw unavailable();
        }
    }

    private JSONObject send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != HttpStatusEnum.OK.getCode()) {
            throw new IllegalStateException("Application会话运维接口返回非成功状态：" + response.statusCode());
        }
        JSONObject envelope = JSON.parseObject(response.body());
        if (envelope == null || envelope.getIntValue("code") != HttpStatusEnum.OK.getCode()) {
            throw new IllegalStateException("Application会话运维接口返回失败结果");
        }
        return envelope;
    }

    private HttpRequest get(String path) {
        return request(path).GET().build();
    }

    private HttpRequest post(String path, JSONObject body) {
        return request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                .build();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(uri(path))
                .timeout(properties.getTimeout())
                .header(OPS_TOKEN_HEADER, properties.getToken());
    }

    private URI uri(String path) {
        String baseUrl = properties.getBaseUrl().endsWith("/")
                ? properties.getBaseUrl().substring(0, properties.getBaseUrl().length() - 1)
                : properties.getBaseUrl();
        return URI.create(baseUrl + path);
    }

    private boolean isConfigured() {
        return properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()
                && properties.getToken() != null && !properties.getToken().isBlank();
    }

    private static JSONObject requireData(JSONObject envelope) {
        JSONObject data = envelope.getJSONObject("data");
        if (data == null) {
            throw new IllegalStateException("Application会话运维接口缺少data");
        }
        return data;
    }

    private static JSONArray requireArray(JSONObject envelope) {
        JSONArray data = envelope.getJSONArray("data");
        if (data == null) {
            throw new IllegalStateException("Application会话运维接口缺少data");
        }
        return data;
    }

    private static ConversationOpsOverviewVO unavailableOverview(String message) {
        return ConversationOpsOverviewVO.builder()
                .sourceAvailable(false)
                .sourceMessage(message)
                .mqAvailable(false)
                .mqMessage("消息队列状态未知")
                .checkedAt(OffsetDateTime.now())
                .dlqByGroup(Map.of())
                .build();
    }

    private static BusinessException unavailable() {
        return new BusinessException(HttpStatusEnum.SERVICE_UNAVAILABLE, "会话运维数据暂不可用");
    }
}

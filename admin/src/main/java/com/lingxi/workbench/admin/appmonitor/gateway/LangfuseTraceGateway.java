package com.lingxi.workbench.admin.appmonitor.gateway;

import cn.hutool.core.text.CharSequenceUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.admin.appmonitor.common.enums.TraceSourceStatus;
import com.lingxi.workbench.admin.appmonitor.config.LangfuseProperties;
import com.lingxi.workbench.admin.appmonitor.model.bo.LangfuseObservationBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.LangfuseTraceResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 通过 Langfuse v4 Observations API 查询单条 Trace，不读取输入输出字段。
 */
@Slf4j
@Component
public class LangfuseTraceGateway {

    private static final String OBSERVATION_FIELDS = "core,basic,model,metrics";

    private final LangfuseProperties properties;
    private final HttpClient httpClient;

    public LangfuseTraceGateway(LangfuseProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.getTimeout()).build();
    }

    public LangfuseTraceResult findTrace(String traceId, OffsetDateTime from, OffsetDateTime to) {
        if (!properties.isApiEnabled()) {
            return result(TraceSourceStatus.DISABLED, "完整链路查询未启用", List.of());
        }
        if (CharSequenceUtil.isBlank(properties.getBaseUrl())) {
            return result(TraceSourceStatus.UNAVAILABLE, "完整链路数据源未配置", List.of());
        }
        if (CharSequenceUtil.isBlank(properties.getPublicKey()) || CharSequenceUtil.isBlank(properties.getSecretKey())) {
            return result(TraceSourceStatus.UNAVAILABLE, "完整链路访问凭据未配置", List.of());
        }
        try {
            HttpResponse<String> response = httpClient.send(request(traceId, from, to), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Langfuse Trace查询失败，traceId={}，statusCode={}", traceId, response.statusCode());
                return result(TraceSourceStatus.UNAVAILABLE, "完整链路查询失败，HTTP " + response.statusCode(), List.of());
            }
            List<LangfuseObservationBO> observations = parse(response.body());
            if (observations.isEmpty()) {
                return result(TraceSourceStatus.EMPTY, "完整链路暂未就绪，数据可能仍在同步", List.of());
            }
            return result(TraceSourceStatus.AVAILABLE, "完整链路数据已同步", observations);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return result(TraceSourceStatus.UNAVAILABLE, "完整链路查询被中断", List.of());
        } catch (RuntimeException | java.io.IOException exception) {
            log.warn("Langfuse Trace查询不可用，traceId={}，errorType={}", traceId, exception.getClass().getSimpleName());
            return result(TraceSourceStatus.UNAVAILABLE, "完整链路数据暂不可用", List.of());
        }
    }

    private HttpRequest request(String traceId, OffsetDateTime from, OffsetDateTime to) {
        String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        String query = "fields=" + encode(OBSERVATION_FIELDS)
                + "&traceId=" + encode(traceId)
                + "&fromStartTime=" + encode(from.toString())
                + "&toStartTime=" + encode(to.toString())
                + "&limit=1000";
        String credential = properties.getPublicKey() + ":" + properties.getSecretKey();
        String authorization = Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
        return HttpRequest.newBuilder(URI.create(baseUrl + "/api/public/v2/observations?" + query))
                .timeout(properties.getTimeout())
                .header("Accept", "application/json")
                .header("Authorization", "Basic " + authorization)
                .GET()
                .build();
    }

    static List<LangfuseObservationBO> parse(String body) {
        JSONObject response = JSONObject.parseObject(body);
        JSONArray data = response.getJSONArray("data");
        if (Objects.isNull(data)) {
            return List.of();
        }
        return data.stream()
                .map(JSONObject.class::cast)
                .map(LangfuseTraceGateway::toObservation)
                .filter(observation -> CharSequenceUtil.isNotBlank(observation.getId()))
                .filter(observation -> Objects.nonNull(observation.getStartTime()))
                .sorted(Comparator.comparing(LangfuseObservationBO::getStartTime))
                .toList();
    }

    private static LangfuseObservationBO toObservation(JSONObject item) {
        return LangfuseObservationBO.builder()
                .id(item.getString("id"))
                .parentObservationId(item.getString("parentObservationId"))
                .name(item.getString("name"))
                .type(item.getString("type"))
                .level(item.getString("level"))
                .providedModelName(item.getString("providedModelName"))
                .startTime(item.getObject("startTime", OffsetDateTime.class))
                .endTime(item.getObject("endTime", OffsetDateTime.class))
                .build();
    }

    private static LangfuseTraceResult result(TraceSourceStatus status, String message, List<LangfuseObservationBO> observations) {
        return new LangfuseTraceResult(status, message, observations);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

}

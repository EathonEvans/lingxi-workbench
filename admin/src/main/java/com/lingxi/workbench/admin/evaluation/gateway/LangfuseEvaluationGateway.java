package com.lingxi.workbench.admin.evaluation.gateway;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.lingxi.workbench.framework.json.JSONArray;
import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.admin.appmonitor.config.LangfuseProperties;
import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationMetric;
import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationSourceStatus;
import com.lingxi.workbench.admin.evaluation.model.bo.EvaluationTraceContext;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseDatasetBO;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseDatasetItemBO;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseDatasetItemsResult;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseDatasetPageResult;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseMetricBO;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseMutationResult;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseOverviewResult;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseScoreBO;
import com.lingxi.workbench.admin.evaluation.model.bo.LangfuseScorePageResult;
import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationCaseRequest;
import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationDatasetRequest;
import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationScoreRequest;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationDatasetQuery;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationScoreQuery;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Langfuse 评测 Public API 适配器。
 *
 * <p>只映射 Score、Dataset 和聚合指标的必要字段，Trace 输入输出由平台业务接口控制。</p>
 */
@Slf4j
public class LangfuseEvaluationGateway {

    private static final double PASSING_SCORE = 4D;
    private static final String MANAGED_BY = "lingxi-workbench";
    private static final int DATASET_ITEM_PAGE_SIZE = 100;
    private static final int MAX_DATASET_ITEMS = 1000;

    private final LangfuseProperties properties;
    private final HttpClient httpClient;

    public LangfuseEvaluationGateway(LangfuseProperties properties) {
        this(properties, HttpClient.newBuilder().connectTimeout(properties.getTimeout()).build());
    }

    LangfuseEvaluationGateway(LangfuseProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public LangfuseOverviewResult overview(OffsetDateTime from, OffsetDateTime to) {
        Optional<LangfuseOverviewResult> unavailable = unavailableOverview();
        if (unavailable.isPresent()) {
            return unavailable.get();
        }
        try {
            List<LangfuseMetricBO> metrics = mergeMetrics(
                    parseMetrics(send(metricsRequest(from, to, false)).body()),
                    parseMetrics(send(metricsRequest(from, to, true)).body()));
            long datasetCount = parseDatasetCount(send(datasetRequest(1, 1)).body());
            return new LangfuseOverviewResult(EvaluationSourceStatus.AVAILABLE, "评测数据已同步", metrics, datasetCount);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return unavailableOverview("评测数据查询被中断");
        } catch (RuntimeException | IOException exception) {
            log.warn("Langfuse评测概览查询不可用，errorType={}", exception.getClass().getSimpleName());
            return unavailableOverview("评测数据源暂不可用");
        }
    }

    public LangfuseScorePageResult scores(EvaluationScoreQuery query) {
        Optional<LangfuseScorePageResult> unavailable = unavailableScores();
        if (unavailable.isPresent()) {
            return unavailable.get();
        }
        try {
            HttpResponse<String> response = send(scoreRequest(query));
            return parseScores(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return unavailableScores("评测明细查询被中断");
        } catch (RuntimeException | IOException exception) {
            log.warn("Langfuse评分查询不可用，errorType={}", exception.getClass().getSimpleName());
            return unavailableScores("评测数据源暂不可用");
        }
    }

    public LangfuseDatasetPageResult datasets(EvaluationDatasetQuery query) {
        Optional<LangfuseDatasetPageResult> unavailable = unavailableDatasets(query);
        if (unavailable.isPresent()) {
            return unavailable.get();
        }
        try {
            HttpResponse<String> response = send(datasetRequest(query.getCurrent(), query.getSize()));
            return parseDatasets(response.body(), query);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return unavailableDatasets(query, "评测数据集查询被中断");
        } catch (RuntimeException | IOException exception) {
            log.warn("Langfuse数据集查询不可用，errorType={}", exception.getClass().getSimpleName());
            return unavailableDatasets(query, "评测数据源暂不可用");
        }
    }

    /** 读取指定时刻的数据集快照，评测运行创建后不再跟随外部数据集变化。 */
    public LangfuseDatasetItemsResult datasetItems(String datasetName, OffsetDateTime datasetVersion) {
        EvaluationSourceStatus status = configurationStatus();
        if (status != EvaluationSourceStatus.AVAILABLE) {
            return LangfuseDatasetItemsResult.builder()
                    .status(status)
                    .message(configurationMessage(status))
                    .datasetName(datasetName)
                    .datasetVersion(datasetVersion)
                    .items(List.of())
                    .build();
        }
        try {
            List<LangfuseDatasetItemBO> items = new ArrayList<>();
            int page = 1;
            int totalPages;
            do {
                JSONObject response = JSONObject.parseObject(send(datasetItemsRequest(
                        datasetName, datasetVersion, page, DATASET_ITEM_PAGE_SIZE)).body());
                items.addAll(parseDatasetItems(response));
                if (items.size() > MAX_DATASET_ITEMS) {
                    throw new IllegalArgumentException("单次评测数据集不能超过1000条用例");
                }
                JSONObject meta = response.getJSONObject("meta");
                totalPages = Objects.isNull(meta) ? 1 : Math.max(meta.getIntValue("totalPages"), 1);
                page++;
            } while (page <= totalPages);
            return LangfuseDatasetItemsResult.builder()
                    .status(EvaluationSourceStatus.AVAILABLE)
                    .message("评测数据集快照已加载")
                    .datasetName(datasetName)
                    .datasetVersion(datasetVersion)
                    .items(List.copyOf(items))
                    .build();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return LangfuseDatasetItemsResult.builder()
                    .status(EvaluationSourceStatus.UNAVAILABLE)
                    .message("评测数据集查询被中断")
                    .datasetName(datasetName)
                    .datasetVersion(datasetVersion)
                    .items(List.of())
                    .build();
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException | IOException exception) {
            log.warn("Langfuse数据集用例查询不可用，datasetName={}，errorType={}",
                    datasetName, exception.getClass().getSimpleName());
            return LangfuseDatasetItemsResult.builder()
                    .status(EvaluationSourceStatus.UNAVAILABLE)
                    .message("评测数据源暂不可用")
                    .datasetName(datasetName)
                    .datasetVersion(datasetVersion)
                    .items(List.of())
                    .build();
        }
    }

    public LangfuseMutationResult createScore(CreateEvaluationScoreRequest request,
                                               EvaluationTraceContext trace, Long operatorId) {
        return mutate("CREATE_SCORE", "/api/public/scores", scoreBody(request, trace, operatorId), "评分已记录");
    }

    public LangfuseMutationResult createDataset(CreateEvaluationDatasetRequest request, Long operatorId) {
        return mutate("CREATE_DATASET", "/api/public/v2/datasets", datasetBody(request, operatorId), "评测数据集已创建");
    }

    public LangfuseMutationResult createCase(String datasetName, CreateEvaluationCaseRequest request,
                                              EvaluationTraceContext trace, Long operatorId) {
        return mutate("CREATE_CASE", "/api/public/dataset-items",
                caseBody(datasetName, request, trace, operatorId), "评测用例已保存");
    }

    private LangfuseMutationResult mutate(String operation, String path, JSONObject body, String successMessage) {
        EvaluationSourceStatus configurationStatus = configurationStatus();
        if (configurationStatus != EvaluationSourceStatus.AVAILABLE) {
            return new LangfuseMutationResult(configurationStatus, configurationMessage(configurationStatus), null);
        }
        try {
            HttpResponse<String> response = send(postRequest(path, body));
            JSONObject result = JSONObject.parseObject(response.body());
            return new LangfuseMutationResult(EvaluationSourceStatus.AVAILABLE, successMessage, result.getString("id"));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new LangfuseMutationResult(EvaluationSourceStatus.UNAVAILABLE, "评测数据写入被中断", null);
        } catch (RuntimeException | IOException exception) {
            log.warn("Langfuse评测写入失败，operation={}，errorType={}", operation, exception.getClass().getSimpleName());
            return new LangfuseMutationResult(EvaluationSourceStatus.UNAVAILABLE, "评测数据源暂不可用", null);
        }
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Langfuse API返回非成功状态：" + response.statusCode());
        }
        return response;
    }

    private HttpRequest metricsRequest(OffsetDateTime from, OffsetDateTime to, boolean passingOnly) {
        JSONObject query = new JSONObject();
        query.put("view", "scores-numeric");
        query.put("dimensions", List.of(Map.of("field", "name")));
        List<Map<String, String>> metrics = new ArrayList<>();
        metrics.add(Map.of("measure", "count", "aggregation", "count"));
        if (!passingOnly) {
            metrics.add(Map.of("measure", "value", "aggregation", "avg"));
        }
        query.put("metrics", metrics);
        JSONArray filters = new JSONArray();
        filters.add(scoreNameFilter());
        if (passingOnly) {
            filters.add(Map.of("column", "value", "operator", ">=", "value", PASSING_SCORE, "type", "number"));
        }
        query.put("filters", filters);
        query.put("fromTimestamp", from.toString());
        query.put("toTimestamp", to.toString());
        query.put("config", Map.of("row_limit", EvaluationMetric.values().length));
        return getRequest("/api/public/v2/metrics?query=" + encode(query.toJSONString()));
    }

    private static Map<String, Object> scoreNameFilter() {
        return Map.of(
                "column", "name",
                "operator", "any of",
                "value", Arrays.stream(EvaluationMetric.values()).map(EvaluationMetric::getScoreName).toList(),
                "type", "stringOptions");
    }

    private HttpRequest scoreRequest(EvaluationScoreQuery query) {
        StringBuilder path = new StringBuilder("/api/public/v3/scores?fields=details,subject&dataType=NUMERIC");
        String names = Objects.nonNull(query.getMetric())
                ? query.getMetric().getScoreName()
                : String.join(",", Arrays.stream(EvaluationMetric.values()).map(EvaluationMetric::getScoreName).toList());
        append(path, "name", names);
        append(path, "limit", String.valueOf(query.getLimit()));
        append(path, "cursor", query.getCursor());
        append(path, "traceId", query.getTraceId());
        return getRequest(path.toString());
    }

    private HttpRequest datasetRequest(int current, int size) {
        return getRequest("/api/public/v2/datasets?page=" + current + "&limit=" + size);
    }

    private HttpRequest datasetItemsRequest(
            String datasetName, OffsetDateTime datasetVersion, int page, int size) {
        StringBuilder path = new StringBuilder("/api/public/dataset-items?");
        path.append("datasetName=").append(encode(datasetName));
        path.append("&version=").append(encode(datasetVersion.toInstant().toString()));
        path.append("&page=").append(page);
        path.append("&limit=").append(size);
        return getRequest(path.toString());
    }

    private HttpRequest getRequest(String path) {
        return requestBuilder(path).GET().build();
    }

    private HttpRequest postRequest(String path, JSONObject body) {
        return requestBuilder(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                .build();
    }

    private HttpRequest.Builder requestBuilder(String path) {
        String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        String credential = properties.getPublicKey() + ":" + properties.getSecretKey();
        String authorization = Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(properties.getTimeout())
                .header("Accept", "application/json")
                .header("Authorization", "Basic " + authorization);
    }

    static List<LangfuseMetricBO> parseMetrics(String body) {
        JSONArray data = JSONObject.parseObject(body).getJSONArray("data");
        if (Objects.isNull(data)) {
            return List.of();
        }
        return data.stream().map(JSONObject.class::cast)
                .map(item -> new LangfuseMetricBO(item.getString("name"), item.getLongValue("count_count"), 0,
                        item.getDoubleValue("avg_value")))
                .filter(metric -> EvaluationMetric.fromScoreName(metric.name()).isPresent())
                .toList();
    }

    static List<LangfuseMetricBO> mergeMetrics(List<LangfuseMetricBO> totals, List<LangfuseMetricBO> passing) {
        Map<EvaluationMetric, Long> passingByMetric = new EnumMap<>(EvaluationMetric.class);
        passing.forEach(metric -> EvaluationMetric.fromScoreName(metric.name())
                .ifPresent(type -> passingByMetric.put(type, metric.total())));
        return totals.stream()
                .map(metric -> new LangfuseMetricBO(metric.name(), metric.total(),
                        EvaluationMetric.fromScoreName(metric.name()).map(passingByMetric::get).orElse(0L),
                        metric.averageValue()))
                .toList();
    }

    static LangfuseScorePageResult parseScores(String body) {
        JSONObject response = JSONObject.parseObject(body);
        JSONArray data = response.getJSONArray("data");
        List<LangfuseScoreBO> scores = Objects.isNull(data) ? List.of() : data.stream()
                .map(JSONObject.class::cast)
                .filter(item -> "NUMERIC".equals(item.getString("dataType")))
                .filter(item -> EvaluationMetric.fromScoreName(item.getString("name")).isPresent())
                .map(LangfuseEvaluationGateway::toScore)
                .toList();
        JSONObject meta = response.getJSONObject("meta");
        String cursor = Objects.isNull(meta) ? null : meta.getString("cursor");
        return new LangfuseScorePageResult(EvaluationSourceStatus.AVAILABLE, "评测明细已同步", scores, cursor);
    }

    static LangfuseDatasetPageResult parseDatasets(String body, EvaluationDatasetQuery query) {
        JSONObject response = JSONObject.parseObject(body);
        JSONArray data = response.getJSONArray("data");
        List<LangfuseDatasetBO> datasets = Objects.isNull(data) ? List.of() : data.stream()
                .map(JSONObject.class::cast)
                .map(LangfuseEvaluationGateway::toDataset)
                .toList();
        JSONObject meta = response.getJSONObject("meta");
        long total = Objects.isNull(meta) ? datasets.size() : meta.getLongValue("totalItems");
        PageResult<LangfuseDatasetBO> page = new PageResult<>(datasets, query.getCurrent(), query.getSize(), total);
        return new LangfuseDatasetPageResult(EvaluationSourceStatus.AVAILABLE, "评测数据集已同步", page);
    }

    static long parseDatasetCount(String body) {
        JSONObject meta = JSONObject.parseObject(body).getJSONObject("meta");
        return Objects.isNull(meta) ? 0L : meta.getLongValue("totalItems");
    }

    static List<LangfuseDatasetItemBO> parseDatasetItems(JSONObject response) {
        JSONArray data = response.getJSONArray("data");
        if (Objects.isNull(data)) {
            return List.of();
        }
        return data.stream().map(JSONObject.class::cast)
                .filter(item -> "ACTIVE".equals(item.getString("status")))
                .map(item -> LangfuseDatasetItemBO.builder()
                        .id(item.getString("id"))
                        .input(item.getJSONObject("input"))
                        .expectedOutput(item.getJSONObject("expectedOutput"))
                        .metadata(item.getJSONObject("metadata"))
                        .sourceTraceId(item.getString("sourceTraceId"))
                        .createdAt(item.getObject("createdAt", OffsetDateTime.class))
                        .updatedAt(item.getObject("updatedAt", OffsetDateTime.class))
                        .build())
                .toList();
    }

    private static LangfuseScoreBO toScore(JSONObject item) {
        JSONObject subject = item.getJSONObject("subject");
        String traceId = null;
        if (Objects.nonNull(subject)) {
            traceId = "observation".equals(subject.getString("kind"))
                    ? subject.getString("traceId") : subject.getString("id");
        }
        return LangfuseScoreBO.builder()
                .id(item.getString("id"))
                .name(item.getString("name"))
                .value(item.getDoubleValue("value"))
                .source(item.getString("source"))
                .traceId(traceId)
                .comment(item.getString("comment"))
                .timestamp(item.getObject("timestamp", OffsetDateTime.class))
                .build();
    }

    private static LangfuseDatasetBO toDataset(JSONObject item) {
        return LangfuseDatasetBO.builder()
                .id(item.getString("id"))
                .name(item.getString("name"))
                .description(item.getString("description"))
                .createdAt(item.getObject("createdAt", OffsetDateTime.class))
                .updatedAt(item.getObject("updatedAt", OffsetDateTime.class))
                .build();
    }

    private static JSONObject scoreBody(CreateEvaluationScoreRequest request,
                                        EvaluationTraceContext trace, Long operatorId) {
        JSONObject metadata = commonMetadata(trace, operatorId);
        metadata.put("scoreScale", "1-5");
        JSONObject body = new JSONObject();
        body.put("id", scoreId(trace.traceId(), request.getMetric()));
        body.put("traceId", trace.traceId());
        body.put("name", request.getMetric().getScoreName());
        body.put("value", request.getValue());
        body.put("dataType", "NUMERIC");
        body.put("source", "API");
        body.put("comment", request.getComment());
        body.put("metadata", metadata);
        return body;
    }

    private static JSONObject datasetBody(CreateEvaluationDatasetRequest request, Long operatorId) {
        JSONObject metadata = new JSONObject();
        metadata.put("managedBy", MANAGED_BY);
        metadata.put("createdBy", String.valueOf(operatorId));
        metadata.put("schemaVersion", 1);
        JSONObject body = new JSONObject();
        body.put("name", request.getName().trim());
        body.put("description", CharSequenceUtil.trim(request.getDescription()));
        body.put("metadata", metadata);
        return body;
    }

    private static JSONObject caseBody(String datasetName, CreateEvaluationCaseRequest request,
                                       EvaluationTraceContext trace, Long operatorId) {
        JSONObject input = new JSONObject();
        input.put("query", request.getInput());
        JSONObject expectedOutput = new JSONObject();
        expectedOutput.put("scenario", request.getScenario().name());
        expectedOutput.put("expectedAction", request.getExpectedAction().name());
        expectedOutput.put("expectedIntentCodes", request.getExpectedIntentCodes());
        expectedOutput.put("highRisk", request.isHighRisk());
        JSONObject metadata = commonMetadata(trace, operatorId);
        metadata.put("note", request.getNote());
        JSONObject body = new JSONObject();
        body.put("id", caseId(datasetName, trace.traceId()));
        body.put("datasetName", datasetName);
        body.put("input", input);
        body.put("expectedOutput", expectedOutput);
        body.put("metadata", metadata);
        body.put("sourceTraceId", trace.traceId());
        body.put("status", "ACTIVE");
        return body;
    }

    private static JSONObject commonMetadata(EvaluationTraceContext trace, Long operatorId) {
        JSONObject metadata = new JSONObject();
        metadata.put("managedBy", MANAGED_BY);
        metadata.put("applicationId", String.valueOf(trace.applicationId()));
        metadata.put("applicationName", trace.applicationName());
        metadata.put("operatorId", String.valueOf(operatorId));
        return metadata;
    }

    private static String caseId(String datasetName, String traceId) {
        return "lingxi-eval-" + DigestUtil.sha256Hex(datasetName + ":" + traceId).substring(0, 32);
    }

    static String scoreId(String traceId, EvaluationMetric metric) {
        return "lingxi-score-" + DigestUtil.sha256Hex(traceId + ":" + metric.getScoreName()).substring(0, 32);
    }

    private Optional<LangfuseOverviewResult> unavailableOverview() {
        EvaluationSourceStatus status = configurationStatus();
        if (status == EvaluationSourceStatus.AVAILABLE) {
            return Optional.empty();
        }
        return Optional.of(new LangfuseOverviewResult(status, configurationMessage(status), List.of(), 0));
    }

    private Optional<LangfuseScorePageResult> unavailableScores() {
        EvaluationSourceStatus status = configurationStatus();
        if (status == EvaluationSourceStatus.AVAILABLE) {
            return Optional.empty();
        }
        return Optional.of(new LangfuseScorePageResult(status, configurationMessage(status), List.of(), null));
    }

    private Optional<LangfuseDatasetPageResult> unavailableDatasets(EvaluationDatasetQuery query) {
        EvaluationSourceStatus status = configurationStatus();
        if (status == EvaluationSourceStatus.AVAILABLE) {
            return Optional.empty();
        }
        PageResult<LangfuseDatasetBO> empty = new PageResult<>(List.of(), query.getCurrent(), query.getSize(), 0);
        return Optional.of(new LangfuseDatasetPageResult(status, configurationMessage(status), empty));
    }

    private static LangfuseOverviewResult unavailableOverview(String message) {
        return new LangfuseOverviewResult(EvaluationSourceStatus.UNAVAILABLE, message, List.of(), 0);
    }

    private static LangfuseScorePageResult unavailableScores(String message) {
        return new LangfuseScorePageResult(EvaluationSourceStatus.UNAVAILABLE, message, List.of(), null);
    }

    private static LangfuseDatasetPageResult unavailableDatasets(EvaluationDatasetQuery query, String message) {
        PageResult<LangfuseDatasetBO> empty = new PageResult<>(List.of(), query.getCurrent(), query.getSize(), 0);
        return new LangfuseDatasetPageResult(EvaluationSourceStatus.UNAVAILABLE, message, empty);
    }

    private EvaluationSourceStatus configurationStatus() {
        if (!properties.isApiEnabled()) {
            return EvaluationSourceStatus.DISABLED;
        }
        if (!missingConfigurationItems().isEmpty()) {
            return EvaluationSourceStatus.MISCONFIGURED;
        }
        return EvaluationSourceStatus.AVAILABLE;
    }

    private String configurationMessage(EvaluationSourceStatus status) {
        if (status == EvaluationSourceStatus.DISABLED) {
            return "评测数据源未启用，请设置 LINGXI_LANGFUSE_API_ENABLED=true 后重启 Admin 后端";
        }
        if (status == EvaluationSourceStatus.MISCONFIGURED) {
            return "评测数据源缺少配置：" + String.join("、", missingConfigurationItems()) + "；补齐后重启 Admin 后端";
        }
        return "评测数据源暂不可用";
    }

    private List<String> missingConfigurationItems() {
        List<String> missingItems = new ArrayList<>();
        if (CharSequenceUtil.isBlank(properties.getBaseUrl())) {
            missingItems.add("服务地址（LINGXI_LANGFUSE_BASE_URL）");
        }
        if (CharSequenceUtil.isBlank(properties.getPublicKey())) {
            missingItems.add("Public Key（LINGXI_LANGFUSE_PUBLIC_KEY）");
        }
        if (CharSequenceUtil.isBlank(properties.getSecretKey())) {
            missingItems.add("Secret Key（LINGXI_LANGFUSE_SECRET_KEY）");
        }
        return missingItems;
    }

    private static void append(StringBuilder path, String name, String value) {
        if (CharSequenceUtil.isNotBlank(value)) {
            path.append('&').append(name).append('=').append(encode(value));
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

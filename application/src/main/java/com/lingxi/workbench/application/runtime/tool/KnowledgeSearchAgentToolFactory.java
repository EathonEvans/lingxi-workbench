package com.lingxi.workbench.application.runtime.tool;

import com.lingxi.workbench.application.runtime.common.enums.KnowledgeSearchStatus;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.knowledge.model.vo.KnowledgeSearchResultVO;
import com.lingxi.workbench.knowledge.service.KnowledgeSearchService;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionMode.DETERMINISTIC_READ_ONLY;

/** 根据AgentVersion冻结配置创建受限的knowledge.search Tool。 */
@Component
public class KnowledgeSearchAgentToolFactory {
    public static final String TOOL_CODE = "knowledge_search";
    private static final double MIN_NEW_EVIDENCE_RATIO = 0.15D;

    private final KnowledgeSearchService knowledgeSearchService;
    private final ModelRuntimeConfigService modelRuntimeConfigService;
    private final TokenCountEstimator tokenCountEstimator;

    public KnowledgeSearchAgentToolFactory(KnowledgeSearchService knowledgeSearchService, ModelRuntimeConfigService modelRuntimeConfigService, TokenCountEstimator tokenCountEstimator) {
        this.knowledgeSearchService = knowledgeSearchService;
        this.modelRuntimeConfigService = modelRuntimeConfigService;
        this.tokenCountEstimator = tokenCountEstimator;
    }

    public AgentTool create(AgentRuntimeDefinition.KnowledgeSearchCapability capability) {
        validate(capability);
        AgentToolDefinition definition = AgentToolDefinition.builder()
                .code(TOOL_CODE)
                .displayName("知识搜索")
                .description("仅在回答需要已发布知识库事实时调用；输入独立、明确的检索查询。返回SATURATED时必须停止搜索并基于已有证据回答")
                .executionMode(DETERMINISTIC_READ_ONLY)
                .build();
        KnowledgeSearchEvidenceTracker evidenceTracker = new KnowledgeSearchEvidenceTracker();
        Function<KnowledgeSearchInput, KnowledgeSearchOutput> executor = input -> execute(capability, input, evidenceTracker);
        ToolCallback callback = FunctionToolCallback.builder(TOOL_CODE, executor)
                .description(definition.getDescription())
                .inputType(KnowledgeSearchInput.class)
                .build();
        return new KnowledgeSearchAgentTool(definition, callback);
    }

    private KnowledgeSearchOutput execute(AgentRuntimeDefinition.KnowledgeSearchCapability capability, KnowledgeSearchInput input,
                                          KnowledgeSearchEvidenceTracker evidenceTracker) {
        if (Objects.isNull(input) || Objects.isNull(input.getQuery()) || input.getQuery().isBlank()) {
            throw new AgentToolException("knowledge.search必须提供非空查询");
        }
        String query = input.getQuery();
        String normalizedQuery = query.trim();
        if (normalizedQuery.length() > capability.getMaxQueryCharacters()) {
            throw new AgentToolException("knowledge.search查询超过字符上限");
        }
        EmbeddingModelRuntimeConfig source = modelRuntimeConfigService.getEmbeddingModelConfig(capability.getEmbeddingModelInstanceId());
        EmbeddingModelRuntimeConfig embeddingConfig = EmbeddingModelRuntimeConfig.builder()
                .instanceId(source.getInstanceId())
                .baseUrl(source.getBaseUrl())
                .apiKey(source.getApiKey())
                .modelCode(source.getModelCode())
                .timeoutSeconds(Math.min(source.getTimeoutSeconds(), capability.getTimeoutSeconds()))
                .maxRetries(source.getMaxRetries())
                .build();
        List<KnowledgeSearchResultVO> results = knowledgeSearchService.searchVersion(
                normalizedQuery, capability.getKnowledgeBaseId(), capability.getKnowledgeIndexVersionId(),
                embeddingConfig, capability.getTopK(), capability.getSimilarityThreshold().doubleValue());
        KnowledgeSearchSelection selection = limitResults(results, capability.getMaxResultTokens());
        KnowledgeSearchProgress progress = evidenceTracker.record(selection.chunkIds());
        return KnowledgeSearchOutput.builder()
                .status(progress.status())
                .resultCount(selection.chunks().size())
                .newEvidenceCount(progress.newEvidenceCount())
                .chunks(selection.chunks())
                .truncated(selection.truncated())
                .instruction(instruction(progress.status()))
                .build();
    }

    private KnowledgeSearchSelection limitResults(List<KnowledgeSearchResultVO> results, int maxTokens) {
        List<KnowledgeSearchChunk> chunks = new ArrayList<>();
        List<Long> chunkIds = new ArrayList<>();
        int remainingTokens = maxTokens;
        boolean truncated = false;
        for (KnowledgeSearchResultVO result : results) {
            String content = Objects.requireNonNullElse(result.getContent(), "").trim();
            int contentTokens = tokenCountEstimator.estimate(content);
            if (contentTokens > remainingTokens) {
                content = limitTokens(content, remainingTokens);
                truncated = true;
            }
            if (content.isBlank()) {
                truncated = true;
                break;
            }
            chunks.add(new KnowledgeSearchChunk(content, result.getSimilarity(), result.getDocumentName(), location(result)));
            chunkIds.add(result.getChunkId());
            remainingTokens -= tokenCountEstimator.estimate(content);
            if (remainingTokens <= 0) {
                truncated = chunks.size() < results.size() || truncated;
                break;
            }
        }
        return new KnowledgeSearchSelection(List.copyOf(chunks), List.copyOf(chunkIds), truncated);
    }

    private static String instruction(KnowledgeSearchStatus status) {
        return switch (status) {
            case FOUND -> "已获得新证据；仅在仍缺少回答所必需的信息时继续检索";
            case EMPTY -> "本次未命中证据；最多更换一次明确的检索角度，然后应回答信息不足或向用户澄清";
            case SATURATED -> "未获得足够的新证据，检索已经饱和；停止调用知识搜索并基于已有证据回答";
        };
    }

    private String limitTokens(String content, int maxTokens) {
        int lower = 0;
        int upper = content.length();
        while (lower < upper) {
            int middle = (lower + upper + 1) / 2;
            if (tokenCountEstimator.estimate(content.substring(0, middle)) <= maxTokens) {
                lower = middle;
            } else {
                upper = middle - 1;
            }
        }
        int end = lower;
        if (end > 0 && Character.isHighSurrogate(content.charAt(end - 1))) {
            end--;
        }
        return content.substring(0, end).trim();
    }

    private static String location(KnowledgeSearchResultVO result) {
        if (Objects.nonNull(result.getPageNumber())) {
            return "第" + result.getPageNumber() + "页";
        }
        return result.getSectionTitle();
    }

    private static void validate(AgentRuntimeDefinition.KnowledgeSearchCapability capability) {
        boolean valid = Objects.nonNull(capability)
                && Objects.nonNull(capability.getKnowledgeBaseId())
                && Objects.nonNull(capability.getKnowledgeIndexVersionId())
                && Objects.nonNull(capability.getEmbeddingModelInstanceId())
                && Objects.nonNull(capability.getTopK()) && capability.getTopK() > 0 && capability.getTopK() <= 100
                && Objects.nonNull(capability.getSimilarityThreshold())
                && capability.getSimilarityThreshold().doubleValue() >= 0D && capability.getSimilarityThreshold().doubleValue() <= 1D
                && Objects.nonNull(capability.getMaxResultTokens()) && capability.getMaxResultTokens() > 0
                && Objects.nonNull(capability.getMaxQueryCharacters()) && capability.getMaxQueryCharacters() > 0
                && Objects.nonNull(capability.getTimeoutSeconds()) && capability.getTimeoutSeconds() > 0;
        if (!valid) {
            throw new AgentToolException("AgentVersion知识搜索配置不完整或超出允许范围");
        }
    }

    private record KnowledgeSearchAgentTool(AgentToolDefinition definition, ToolCallback callback) implements AgentTool {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class KnowledgeSearchInput {
        @ToolParam(description = "面向知识库的独立检索查询，不包含知识库ID或版本ID", required = true)
        private String query;
    }

    /**
     * @param content      命中的知识正文
     * @param score        余弦相似度
     * @param documentName 来源文档名称
     * @param location     页码或章节位置
     */
    public record KnowledgeSearchChunk(String content, Double score, String documentName, String location) {
    }

    /**
     * @param chunks    实际回传模型的知识片段
     * @param chunkIds  与片段顺序一致的Chunk ID
     * @param truncated 是否因Token预算截断
     */
    private record KnowledgeSearchSelection(List<KnowledgeSearchChunk> chunks, List<Long> chunkIds, boolean truncated) {
    }

    /**
     * @param status           当前检索进展状态
     * @param newEvidenceCount 本轮新增证据数量
     */
    private record KnowledgeSearchProgress(KnowledgeSearchStatus status, int newEvidenceCount) {
    }

    /** 单次 Agent 执行内跟踪已提供给模型的知识证据。 */
    private static final class KnowledgeSearchEvidenceTracker {

        private final Set<Long> seenChunkIds = new HashSet<>();
        private int emptyAttempts;

        private synchronized KnowledgeSearchProgress record(List<Long> chunkIds) {
            List<Long> validChunkIds = chunkIds.stream().filter(Objects::nonNull).distinct().toList();
            if (validChunkIds.isEmpty()) {
                emptyAttempts++;
                KnowledgeSearchStatus status = KnowledgeSearchStatus.EMPTY;
                if (emptyAttempts > 1) {
                    status = KnowledgeSearchStatus.SATURATED;
                }
                return new KnowledgeSearchProgress(status, 0);
            }
            int newEvidenceCount = (int) validChunkIds.stream().filter(seenChunkIds::add).count();
            double newEvidenceRatio = (double) newEvidenceCount / validChunkIds.size();
            KnowledgeSearchStatus status = KnowledgeSearchStatus.FOUND;
            if (newEvidenceCount == 0 || newEvidenceRatio < MIN_NEW_EVIDENCE_RATIO) {
                status = KnowledgeSearchStatus.SATURATED;
            }
            return new KnowledgeSearchProgress(status, newEvidenceCount);
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class KnowledgeSearchOutput {
        private KnowledgeSearchStatus status;
        private int resultCount;
        private int newEvidenceCount;
        private List<KnowledgeSearchChunk> chunks;
        private boolean truncated;
        private String instruction;
    }
}

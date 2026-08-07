package com.lingxi.workbench.application.runtime.tool;

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
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** 根据AgentVersion冻结配置创建受限的knowledge.search Tool。 */
@Component
public class KnowledgeSearchAgentToolFactory {
    public static final String TOOL_CODE = "knowledge_search";

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
                .description("仅在回答需要已发布知识库事实时调用；输入独立、明确的检索查询")
                .build();
        Function<KnowledgeSearchInput, KnowledgeSearchOutput> executor = input -> execute(capability, input);
        ToolCallback callback = FunctionToolCallback.builder(TOOL_CODE, executor)
                .description(definition.getDescription())
                .inputType(KnowledgeSearchInput.class)
                .build();
        return new KnowledgeSearchAgentTool(definition, callback);
    }

    private KnowledgeSearchOutput execute(AgentRuntimeDefinition.KnowledgeSearchCapability capability, KnowledgeSearchInput input) {
        String query = Objects.isNull(input) ? null : input.getQuery();
        if (Objects.isNull(query) || query.isBlank()) {
            throw new AgentToolException("knowledge.search必须提供非空查询");
        }
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
        return limitResults(results, capability.getMaxResultTokens());
    }

    private KnowledgeSearchOutput limitResults(List<KnowledgeSearchResultVO> results, int maxTokens) {
        List<KnowledgeSearchChunk> chunks = new ArrayList<>();
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
            remainingTokens -= tokenCountEstimator.estimate(content);
            if (remainingTokens <= 0) {
                truncated = chunks.size() < results.size() || truncated;
                break;
            }
        }
        return KnowledgeSearchOutput.builder().chunks(List.copyOf(chunks)).truncated(truncated).build();
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

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class KnowledgeSearchOutput {
        private List<KnowledgeSearchChunk> chunks;
        private boolean truncated;
    }
}

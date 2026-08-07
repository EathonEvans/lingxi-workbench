package com.lingxi.workbench.application.runtime.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * AgentVersion JSONB 快照在运行端的只读业务契约。
 *
 * <p>管理端负责创建和校验快照，应用端通过 Fastjson2 将已发布快照反序列化为该对象。
 * 因此保留无参构造和 Setter 供 JSON 框架赋值；业务测试和手工构造统一使用 Builder，
 * 避免十二个位置参数发生错位。</p>
 *
 * <p>该模型刻意不依赖 admin 模块，避免运行服务反向依赖控制面。</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentRuntimeDefinition {

    /**
     * 快照结构版本号，用于兼容性校验。
     */
    private Integer schemaVersion;
    /**
     * Agent 类型标识，如 RAG_CHAT、SUPERVISOR。
     */
    private String agentType;
    /**
     * 回答阶段使用的对话模型实例主键。
     */
    private Long chatModelInstanceId;
    /**
     * 问题改写阶段使用的模型实例主键。
     */
    private Long rewriteModelInstanceId;
    /**
     * Schema V2 回答阶段绑定的 SYSTEM PromptVersion 主键。
     */
    private Long systemPromptVersionId;
    /**
     * Schema V2 回答阶段绑定的 USER PromptVersion 主键。
     */
    private Long userPromptVersionId;
    /**
     * Schema V1 回答 PromptVersion，历史语义等同于 SYSTEM Prompt。
     */
    private Long answerPromptVersionId;
    /**
     * 问题改写阶段绑定的已发布 PromptVersion 主键。
     */
    private Long rewritePromptVersionId;
    /**
     * Schema V3 允许暴露给回答模型的本地工具编码。
     */
    private List<String> toolCodes;
    /**
     * Schema V3 工具调用边界。
     */
    private ToolPolicy toolPolicy;
    /**
     * 知识库及其 Embedding 模型绑定。
     */
    private KnowledgeBinding knowledge;
    /** Schema V4 ReAct知识搜索Tool的冻结资源与预算。 */
    private KnowledgeSearchCapability knowledgeSearch;
    /**
     * 多轮记忆策略。
     */
    private MemoryPolicy memoryPolicy;
    /**
     * 问题改写策略。
     */
    private RewritePolicy rewritePolicy;
    /**
     * 向量检索策略。
     */
    private RetrievalPolicy retrievalPolicy;
    /**
     * 回答模型生成参数。
     */
    private GenerationPolicy generationPolicy;
    /**
     * 单次 Agent 执行的全局资源限制。
     */
    private RuntimePolicy runtimePolicy;

    /**
     * 兼容读取 Schema V1 已发布快照中的回答 System Prompt。
     */
    public Long resolveSystemPromptVersionId() {
        if (systemPromptVersionId != null) {
            return systemPromptVersionId;
        }
        return answerPromptVersionId;
    }

    /**
     * 兼容 V1/V2 快照：未声明工具时按空列表处理。
     */
    public List<String> resolveToolCodes() {
        return toolCodes == null ? List.of() : List.copyOf(toolCodes);
    }

    /**
     * 知识库绑定配置。
     *
     * @param knowledgeBaseId          知识库主键
     * @param embeddingModelInstanceId Embedding 模型实例主键
     * @param bindingMode              绑定模式，如 MANUAL / AUTO
     * @param priority                 优先级
     */
    public record KnowledgeBinding(Long knowledgeBaseId, Long embeddingModelInstanceId, String bindingMode,
                                   Integer priority) {
    }

    /** ReAct知识搜索Tool的服务端可信配置。 */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class KnowledgeSearchCapability {
        private Long knowledgeBaseId;
        private Long knowledgeIndexVersionId;
        private Long embeddingModelInstanceId;
        private Integer topK;
        private BigDecimal similarityThreshold;
        private Integer maxResultTokens;
        private Integer maxQueryCharacters;
        private Integer timeoutSeconds;
    }

    /**
     * 多轮记忆策略。
     *
     * @param enabled   是否启用多轮记忆
     * @param maxRounds 最多携带的完整问答轮数
     * @param maxTokens 历史消息允许占用的最大 Token 数
     */
    public record MemoryPolicy(Boolean enabled, Integer maxRounds, Integer maxTokens) {
    }

    /**
     * 问题改写策略。
     *
     * <p>字段较多且大部分可以按 Agent 类型选择性配置，因此使用 Builder，
     * 不使用长位置参数构造。</p>
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class RewritePolicy {

        /**
         * 是否启用问题改写。
         */
        private Boolean enabled;
        /**
         * 改写参考的最大历史轮数。
         */
        private Integer maxHistoryRounds;
        /**
         * 改写历史输入的 Token 上限。
         */
        private Integer maxHistoryTokens;
        /**
         * 改写模型温度。改写是确定性任务，通常应配置为接近 0 的低温度。
         */
        private BigDecimal temperature;
        /**
         * 改写模型最大输出 Token 数。
         */
        private Integer maxOutputTokens;
        /**
         * 改写结果最大字符数。
         */
        private Integer maxOutputCharacters;
        /**
         * 改写调用超时毫秒数。
         */
        private Integer timeoutMillis;
        /**
         * 改写失败时是否回退到原始问题。
         */
        private Boolean fallbackToOriginal;
    }

    /**
     * 向量检索策略。
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class RetrievalPolicy {

        /**
         * 最多返回的 Chunk 数。
         */
        private Integer topK;
        /**
         * 最低相似度阈值。
         */
        private BigDecimal similarityThreshold;
        /**
         * 知识上下文最大 Token 数。
         */
        private Integer maxContextTokens;
        /**
         * 空召回策略：RETURN_EMPTY_HINT / CONTINUE。
         */
        private String emptyResultStrategy;
        /**
         * 检索失败策略：FAIL_REQUEST / DEGRADE。
         */
        private String failureStrategy;
    }

    /**
     * 模型回答生成参数。
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GenerationPolicy {

        /**
         * 温度参数，控制输出随机性。
         */
        private BigDecimal temperature;
        /**
         * 核采样参数。
         */
        private BigDecimal topP;
        /**
         * 最大输出 Token 数。
         */
        private Integer maxOutputTokens;
        /**
         * 单次模型调用超时秒数。
         */
        private Integer timeoutSeconds;
        /**
         * 模型调用失败后的最大重试次数。
         */
        private Integer maxRetries;
    }

    /**
     * 运行时全局限制策略。
     *
     * @param totalTimeoutSeconds 整个 Agent 节点执行的总超时秒数
     * @param maxModelCalls       最多模型调用次数
     * @param maxInputTokens      输入 Token 总数上限
     * @param maxOutputTokens     输出 Token 总数上限
     */
    public record RuntimePolicy(Integer totalTimeoutSeconds, Integer maxModelCalls, Integer maxInputTokens,
                                Integer maxOutputTokens) {
    }

    /**
     * 本地工具调用边界。
     *
     * @param maxToolCalls        最多工具调用次数
     * @param maxResultCharacters 单次工具结果最大字符数
     */
    public record ToolPolicy(Integer maxToolCalls, Integer maxResultCharacters) {
    }
}

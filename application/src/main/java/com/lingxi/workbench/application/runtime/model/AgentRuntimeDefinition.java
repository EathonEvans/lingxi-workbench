package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.infra.ai.agent.model.PlanPolicy;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * AgentVersion JSONB 快照在运行端的只读业务契约。
 *
 * <p>管理端负责创建和校验快照，应用端通过 Fastjson2 将已发布快照反序列化为该对象。
 * 因此保留无参构造和 Setter 供 JSON 框架赋值；业务测试和手工构造统一使用 Builder，
     * 避免长位置参数发生错位。</p>
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
     * 快照结构版本号。
     */
    private Integer schemaVersion;
    /**
     * Agent 类型。
     */
    private AgentRole role;
    /**
     * 回答阶段使用的对话模型实例主键。
     */
    private Long chatModelInstanceId;
    /**
     * Schema V2 回答阶段绑定的 SYSTEM PromptVersion 主键。
     */
    private Long systemPromptVersionId;
    /**
     * Schema V2 回答阶段绑定的 USER PromptVersion 主键。
     */
    private Long userPromptVersionId;
    /**
     * Schema V3 允许暴露给回答模型的本地工具编码。
     */
    private List<String> toolCodes;
    /**
     * Schema V3 工具调用边界。
     */
    private ToolPolicy toolPolicy;
    /**
     * Schema V4 ReAct知识搜索Tool的冻结资源与预算。
     */
    private KnowledgeSearchCapability knowledgeSearch;
    /**
     * 回答模型生成参数。
     */
    private GenerationPolicy generationPolicy;
    /**
     * 主Agent的结构性规划边界。
     */
    private PlanPolicy planPolicy;

    /** 返回发布快照冻结的工具编码。 */
    public List<String> resolveToolCodes() {
        return List.copyOf(Objects.requireNonNull(toolCodes, "AgentVersion工具编码不能为空"));
    }

    /**
     * ReAct知识搜索Tool的服务端可信配置。
     */
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
    }

    /**
     * 本地工具调用边界。
     *
     * @param maxResultCharacters 单次工具结果最大字符数
     */
    public record ToolPolicy(Integer maxResultCharacters) {
    }
}

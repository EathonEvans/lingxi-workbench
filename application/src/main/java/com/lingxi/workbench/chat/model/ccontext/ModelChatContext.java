package com.lingxi.workbench.chat.model.ccontext;

import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.chat.common.enums.RetrievalStatus;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.PromptPair;
import com.lingxi.workbench.chat.model.bo.QueryRewriteConfig;
import com.lingxi.workbench.knowledge.model.vo.KnowledgeSearchResultVO;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Optional;

/**
 * 单轮对话前置处理链的共享上下文。
 *
 * <p>用户、会话、问题和历史消息通过 RuntimeVariables 共享。处理链只产生历史、
 * 记忆和旧版检索结果，最终 Prompt 由 ModelRequestFactory 在模型边界直接编译。</p>
 */
@Getter
@Builder
public class ModelChatContext {

    /**
     * 单次对话执行唯一的运行时变量容器。
     *
     * <p>用户、会话、原始问题和历史消息只保存在这里，不在 Chat Context 中重复建模。</p>
     */
    private RuntimeVariables runtimeVariables;
    /** 路由前准备完成的共享上下文；旧调用链兼容场景可以为空。 */
    private ConversationContext conversationContext;
    /*
     * 以下字段来自已发布 AgentVersion，描述该版本对本轮执行施加的策略。
     * 字段为空只能表示当前 Agent 类型未启用对应能力，不能回退到请求参数或平台默认配置。
     */
    /**
     * 本轮需要检索的知识库主键；为空时跳过知识检索。
     */
    private Long knowledgeBaseId;
    /**
     * 与知识库索引匹配的 Embedding 模型实例主键。
     */
    private Long embeddingModelInstanceId;
    /**
     * 是否启用窗口记忆。
     */
    private Boolean memoryEnabled;
    /**
     * 最多读取的完整问答轮数。
     */
    private Integer memoryMaxRounds;
    /**
     * 注入历史消息的最大 Token 数。
     */
    private Integer memoryMaxTokens;
    /**
     * Application 级记忆系统配置（向量模型、Chat模型、阈值等）。
     * 为空或 disabled 时跳过长期记忆注入和后处理。
     */
    @Setter
    private MemoryConfig memoryConfig;
    /**
     * AgentVersion 解析后的问题改写节点配置。
     */
    private QueryRewriteConfig queryRewriteConfig;
    /**
     * 向量召回的最大结果数量。
     */
    private Integer retrievalTopK;
    /**
     * 向量召回最低相似度。
     */
    private Double retrievalSimilarityThreshold;
    /**
     * 检索异常后的处理策略。
     */
    private String retrievalFailureStrategy;
    /**
     * AgentVersion 绑定的已发布 SYSTEM PromptVersion 快照。
     */
    private PromptRuntimeSnapshot systemPrompt;
    /**
     * AgentVersion 绑定的已发布 USER PromptVersion 快照。
     *
     * <p>Schema V1 历史版本没有该字段，运行时保留旧的 User 消息组装方式。</p>
     */
    private PromptRuntimeSnapshot userPrompt;
    /**
     * 本轮用户和 Assistant 消息的持久化标识。
     */
    private ChatTurnBO turn;
    /**
     * 将问题改写等中间模型调用的 Token 用量汇总到本次执行。
     */
    private ModelUsageRecorder modelUsageRecorder;

    /*
     * 以下字段由准备过程按照装配顺序逐步写入。
     */
    /**
     * 按相关度排序的知识库召回片段。
     */
    @Setter
    private List<KnowledgeSearchResultVO> knowledgeChunks;
    /**
     * 知识检索阶段的可观测结果状态。
     */
    @Setter
    private RetrievalStatus retrievalStatus;
    /**
     * ModelRequestFactory 编译后可直接交给模型网关的用户 Prompt 与 System Prompt。
     */
    @Setter
    private PromptPair promptPair;

    /**
     * 返回当前阶段向量召回应使用的问题。
     *
     * <p>该变量无论是否启用问题改写都由 QueryRewriteNode 负责写入，因此检索节点
     * 不需要知道回退原问题、模型失败等上游分支。</p>
     */
    public String retrievalQuery() {
        return runtimeVariables.require(
                RuntimeVariableAccessors.KNOWLEDGE_RETRIEVAL_QUERY);
    }

    /** 汇总一次处理链内部模型调用的 Token 用量。 */
    public void recordModelUsage(TokenUsage usage) {
        Optional.ofNullable(modelUsageRecorder).ifPresent(recorder -> recorder.record(usage));
    }
}

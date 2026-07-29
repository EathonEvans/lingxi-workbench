package com.lingxi.workbench.chat.model.ccontext;

import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.chat.common.enums.RetrievalStatus;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.PromptPair;
import com.lingxi.workbench.chat.model.bo.QueryRewriteConfig;
import com.lingxi.workbench.knowledge.model.vo.KnowledgeSearchResultVO;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 单轮对话前置处理链的共享上下文。
 *
 * <p>请求参数和本轮消息标识在创建后不应被节点修改；用户、会话、问题和历史消息
 * 通过 RuntimeVariables 共享，召回结果和最终 Prompt 由对应节点按顺序写入。</p>
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
    /**
     * 客户端幂等消息标识。
     */
    private String clientMessageId;

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
     * 以下字段由处理链节点按照装配顺序逐步写入。
     * 检索节点和 Prompt 节点分别只负责自己的输出。
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
     * 可直接交给模型网关的用户 Prompt 与 System Prompt。
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
    public void recordModelUsage(
            Integer inputTokens,
            Integer outputTokens,
            Integer totalTokens) {
        if (modelUsageRecorder != null) {
            modelUsageRecorder.record(inputTokens, outputTokens, totalTokens);
        }
    }
}

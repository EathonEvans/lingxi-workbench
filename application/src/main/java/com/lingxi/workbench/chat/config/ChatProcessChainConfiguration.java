package com.lingxi.workbench.chat.config;

import com.lingxi.workbench.chat.service.ChatProcessChain;
import com.lingxi.workbench.chat.service.node.ConversationHistoryNode;
import com.lingxi.workbench.chat.service.node.KnowledgeRetrievalNode;
import com.lingxi.workbench.chat.service.node.PromptBuildNode;
import com.lingxi.workbench.chat.service.node.QueryRewriteNode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 显式声明对话前置处理链的业务顺序。
 */
@Configuration
public class ChatProcessChainConfiguration {

    /**
     * 按业务依赖显式装配单向处理链。
     *
     * <p>当前顺序先准备两套独立的历史窗口，再生成检索问题、执行知识检索，
     * 最后由 Prompt 节点消费召回结果。显式装配可以直接从这里阅读完整流程，
     * 也避免依赖 Spring Bean 扫描顺序。</p>
     *
     * @param historyNode   会话历史加载节点
     * @param queryRewriteNode 问题改写节点
     * @param retrievalNode 知识库向量检索节点
     * @param promptBuildNode 模型 Prompt 构建节点
     * @return 从历史加载节点开始执行的对话处理链
     */
    @Bean
    public ChatProcessChain chatProcessChain(
            ConversationHistoryNode historyNode,
            QueryRewriteNode queryRewriteNode,
            KnowledgeRetrievalNode retrievalNode,
            PromptBuildNode promptBuildNode) {
        historyNode.next(queryRewriteNode)
                .next(retrievalNode)
                .next(promptBuildNode);
        return new ChatProcessChain(historyNode);
    }
}

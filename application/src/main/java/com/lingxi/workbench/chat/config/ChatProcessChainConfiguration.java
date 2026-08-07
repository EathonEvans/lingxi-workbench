package com.lingxi.workbench.chat.config;

import com.lingxi.workbench.chat.service.ChatProcessChain;
import com.lingxi.workbench.chat.service.node.ConversationHistoryNode;
import com.lingxi.workbench.chat.service.node.KnowledgeRetrievalNode;
import com.lingxi.workbench.chat.service.node.MemoryInjectionNode;
import com.lingxi.workbench.chat.service.node.QueryRewriteNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 显式声明旧 AgentVersion 的兼容处理链顺序。
 */
@Configuration
public class ChatProcessChainConfiguration {

    /**
     * 按业务依赖显式装配单向处理链。
     *
     * <p>该链仅服务历史 RAG_CHAT 版本：投影共享上下文中的历史和记忆后，继续执行
     * 旧问题改写、自动知识检索和 Prompt 构建。新 ReAct 路径不得接入该链。</p>
     *
     * @param historyNodes      会话历史加载节点
     * @param memoryNodes       用户记忆节点
     * @param queryRewriteNodes 问题改写节点
     * @param retrievalNodes    知识库向量检索节点
     * @return 从历史加载节点开始执行的对话处理链
     */
    @Bean("ragChatProcessChain")
    public ChatProcessChain ragChatProcessChain(ObjectProvider<ConversationHistoryNode> historyNodes, ObjectProvider<MemoryInjectionNode> memoryNodes, ObjectProvider<QueryRewriteNode> queryRewriteNodes, ObjectProvider<KnowledgeRetrievalNode> retrievalNodes) {
        ConversationHistoryNode historyNode = historyNodes.getObject();
        MemoryInjectionNode memoryNode = memoryNodes.getObject();
        QueryRewriteNode queryRewriteNode = queryRewriteNodes.getObject();
        KnowledgeRetrievalNode retrievalNode = retrievalNodes.getObject();
        historyNode.next(memoryNode)
                .next(queryRewriteNode)
                .next(retrievalNode);
        return new ChatProcessChain(historyNode);
    }

    /**
     * 历史 Supervisor 版本复用共享历史和长期记忆后组装 Prompt，不进入自动知识检索。
     */
    @Bean("supervisorChatProcessChain")
    public ChatProcessChain supervisorChatProcessChain(ObjectProvider<ConversationHistoryNode> historyNodes, ObjectProvider<MemoryInjectionNode> memoryNodes) {
        ConversationHistoryNode historyNode = historyNodes.getObject();
        historyNode.next(memoryNodes.getObject());
        return new ChatProcessChain(historyNode);
    }
}

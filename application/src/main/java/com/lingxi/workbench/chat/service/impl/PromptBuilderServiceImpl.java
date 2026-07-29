package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.chat.model.bo.PromptPair;
import com.lingxi.workbench.chat.model.ccontext.PromptContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.service.PromptBuilderService;
import com.lingxi.workbench.knowledge.model.vo.KnowledgeSearchResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 根据用户原始问题和知识库召回片段构建模型输入。
 */
@Service
@RequiredArgsConstructor
public class PromptBuilderServiceImpl implements PromptBuilderService {

    private static final String KNOWLEDGE_SYSTEM_PROMPT = """
            请优先依据给定的知识库内容回答问题。
            如果知识库内容不足以支持结论，请明确说明，不要编造。
            """;

    private final PromptTemplateRenderer promptTemplateRenderer;

    /**
     * 根据用户问题和知识库召回片段构建模型 Prompt。
     * 有召回片段时将其格式化为带编号的知识库内容拼入 userPrompt，
     * 并在 systemPrompt 中追加知识优先指令。
     */
    @Override
    public PromptPair build(PromptContext context) {
        String renderedSystemPrompt = promptTemplateRenderer
                .renderSystemPrompt(
                        context.getSystemPromptSnapshot(),
                        context.getRuntimeVariables());
        List<KnowledgeSearchResultVO> chunks = context.getKnowledgeChunks();
        String knowledgeContext = formatKnowledgeContext(chunks);
        context.getRuntimeVariables().put(
                RuntimeVariableAccessors.KNOWLEDGE_CONTEXT,
                knowledgeContext);

        /*
         * Schema V1 已发布版本没有 User PromptVersion。旧版本保持原有消息格式，
         * 新发布的 Schema V2 Agent 必须走可配置的 USER Prompt。
         */
        if (context.getUserPromptSnapshot() == null) {
            return buildLegacyPrompt(
                    context,
                    renderedSystemPrompt,
                    knowledgeContext);
        }
        String renderedUserPrompt = promptTemplateRenderer.renderUserPrompt(
                context.getUserPromptSnapshot(),
                context.getRuntimeVariables());
        String systemPrompt = normalizeSystemPrompt(renderedSystemPrompt);
        if (StringUtils.hasText(knowledgeContext)) {
            systemPrompt = buildKnowledgeSystemPrompt(renderedSystemPrompt);
        }
        return new PromptPair(
                renderedUserPrompt,
                systemPrompt);
    }

    private static String formatKnowledgeContext(
            List<KnowledgeSearchResultVO> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        StringBuilder contextText = new StringBuilder(2048);
        for (int index = 0; index < chunks.size(); index++) {
            contextText.append('[')
                    .append(index + 1)
                    .append("] ")
                    .append(chunks.get(index).getContent())
                    .append('\n');
        }
        return contextText.toString().trim();
    }

    private static PromptPair buildLegacyPrompt(
            PromptContext context,
            String renderedSystemPrompt,
            String knowledgeContext) {
        String query = context.getRuntimeVariables().require(
                RuntimeVariableAccessors.USER_QUERY);
        if (!StringUtils.hasText(knowledgeContext)) {
            return new PromptPair(
                    query,
                    normalizeSystemPrompt(renderedSystemPrompt));
        }
        String userPrompt = "知识库内容：\n\n"
                + knowledgeContext
                + "\n\n用户问题："
                + query;
        return new PromptPair(
                userPrompt,
                buildKnowledgeSystemPrompt(renderedSystemPrompt));
    }

    /**
     * Agent System Prompt 位于前，RAG 的事实约束位于后。
     * 两者都属于系统消息，不能拼入用户问题来伪装用户输入。
     */
    private static String buildKnowledgeSystemPrompt(String agentSystemPrompt) {
        if (!StringUtils.hasText(agentSystemPrompt)) {
            return KNOWLEDGE_SYSTEM_PROMPT;
        }
        return agentSystemPrompt.trim() + "\n\n" + KNOWLEDGE_SYSTEM_PROMPT;
    }

    private static String normalizeSystemPrompt(String systemPrompt) {
        if (!StringUtils.hasText(systemPrompt)) {
            return "";
        }
        return systemPrompt.trim();
    }
}

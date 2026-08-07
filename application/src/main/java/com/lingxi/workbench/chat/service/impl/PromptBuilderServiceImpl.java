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
import java.util.Map;

/**
 * 根据用户原始问题和知识库召回片段构建模型输入。
 */
@Service
@RequiredArgsConstructor
public class PromptBuilderServiceImpl implements PromptBuilderService {

    private static final String DEFAULT_KNOWLEDGE_SYSTEM_PROMPT = """
            请优先依据给定的知识库内容回答问题。
            如果知识库内容不足以支持结论，请明确说明，不要编造。
            """;
    private static final String DEFAULT_KNOWLEDGE_USER_PROMPT = """
            知识库内容：

            {{knowledge_context}}

            用户问题：{{user_query}}
            """;

    private final PromptTemplateRenderer promptTemplateRenderer;

    @Override
    public PromptPair build(PromptContext context) {
        String renderedSystemPrompt = promptTemplateRenderer
                .renderSystemPrompt(context.getSystemPromptSnapshot(), context.getRuntimeVariables());
        List<KnowledgeSearchResultVO> chunks = context.getKnowledgeChunks();
        String knowledgeContext = formatKnowledgeContext(chunks);
        context.getRuntimeVariables().put(RuntimeVariableAccessors.KNOWLEDGE_CONTEXT, knowledgeContext);

        if (context.getUserPromptSnapshot() == null) {
            return buildDefaultPrompt(context, renderedSystemPrompt, knowledgeContext);
        }
        String renderedUserPrompt = promptTemplateRenderer.renderUserPrompt(
                context.getUserPromptSnapshot(), context.getRuntimeVariables());
        String systemPrompt = normalizeSystemPrompt(renderedSystemPrompt);
        if (StringUtils.hasText(knowledgeContext)) {
            systemPrompt = buildKnowledgeSystemPrompt(context, renderedSystemPrompt);
        }
        return new PromptPair(renderedUserPrompt, systemPrompt);
    }

    private static String formatKnowledgeContext(List<KnowledgeSearchResultVO> chunks) {
        if (chunks == null || chunks.isEmpty()) return "";
        StringBuilder contextText = new StringBuilder(2048);
        for (int index = 0; index < chunks.size(); index++) {
            contextText.append('[').append(index + 1).append("] ")
                    .append(chunks.get(index).getContent()).append('\n');
        }
        return contextText.toString().trim();
    }

    private PromptPair buildDefaultPrompt(PromptContext context, String renderedSystemPrompt, String knowledgeContext) {
        String query = context.getRuntimeVariables().require(RuntimeVariableAccessors.USER_QUERY);
        if (!StringUtils.hasText(knowledgeContext)) {
            return new PromptPair(query, normalizeSystemPrompt(renderedSystemPrompt));
        }
        String userPrompt = renderKnowledgePrompt(DEFAULT_KNOWLEDGE_USER_PROMPT, Map.of(
                "knowledge_context", knowledgeContext,
                "user_query", query));
        return new PromptPair(userPrompt, buildKnowledgeSystemPrompt(context, renderedSystemPrompt));
    }

    private String buildKnowledgeSystemPrompt(PromptContext context, String agentSystemPrompt) {
        String knowledgeInstruction = renderKnowledgePrompt(DEFAULT_KNOWLEDGE_SYSTEM_PROMPT, Map.of());
        if (!StringUtils.hasText(agentSystemPrompt)) return knowledgeInstruction;
        return agentSystemPrompt.trim() + "\n\n" + knowledgeInstruction;
    }

    private static String normalizeSystemPrompt(String systemPrompt) {
        if (!StringUtils.hasText(systemPrompt)) return "";
        return systemPrompt.trim();
    }

    private static String renderKnowledgePrompt(String template, Map<String, String> variables) {
        for (Map.Entry<String, String> variable : variables.entrySet()) {
            template = template.replace("{{" + variable.getKey() + "}}", variable.getValue());
        }
        if (template.contains("{{") || template.contains("}}")) {
            throw new IllegalStateException("知识库Prompt存在未解析变量");
        }
        return template.trim();
    }
}

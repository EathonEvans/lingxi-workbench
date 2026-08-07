package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.chat.model.bo.PromptPair;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.model.ccontext.PromptContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.service.PromptBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 在模型调用边界把已准备的可信上下文编译为模型消息。
 *
 * <p>该对象不参与业务处理链，也不产生独立运行事件。旧 RAG Agent 与 Supervisor
 * 在各自准备策略完成后直接调用；后续 ReAct 和 Planner 可在这里统一扩展预算约束。</p>
 */
@Component
@RequiredArgsConstructor
public class ModelRequestFactory {

    private final PromptBuilderService promptBuilderService;
    private final PromptTemplateRenderer promptTemplateRenderer;

    /** 编译包含旧版自动检索结果的模型 Prompt。 */
    public PromptPair createRetrievalPrompt(ModelChatContext context) {
        PromptContext promptContext = PromptContext.builder()
                .knowledgeChunks(context.getKnowledgeChunks())
                .systemPromptSnapshot(context.getSystemPrompt())
                .userPromptSnapshot(context.getUserPrompt())
                .runtimeVariables(context.getRuntimeVariables())
                .memoryConfig(context.getMemoryConfig())
                .build();
        return promptBuilderService.build(promptContext);
    }

    /** 编译不包含自动知识检索的模型 Prompt。 */
    public PromptPair createDirectPrompt(ModelChatContext context) {
        String systemPrompt = promptTemplateRenderer.renderSystemPrompt(context.getSystemPrompt(), context.getRuntimeVariables()).trim();
        String userPrompt = Optional.ofNullable(context.getUserPrompt())
                .map(prompt -> promptTemplateRenderer.renderUserPrompt(prompt, context.getRuntimeVariables()))
                .orElseGet(() -> context.getRuntimeVariables().require(RuntimeVariableAccessors.USER_QUERY));
        return new PromptPair(userPrompt, systemPrompt);
    }
}

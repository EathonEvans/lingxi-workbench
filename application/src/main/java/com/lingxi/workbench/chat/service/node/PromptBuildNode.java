package com.lingxi.workbench.chat.service.node;

import com.lingxi.workbench.chat.common.enums.ChatProcessResult;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.model.ccontext.PromptContext;
import com.lingxi.workbench.chat.service.AbstractChatProcessNode;
import com.lingxi.workbench.chat.service.PromptBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 将用户问题和召回片段构建为最终模型 Prompt。
 */
@Component
@RequiredArgsConstructor
public class PromptBuildNode extends AbstractChatProcessNode {

    private final PromptBuilderService promptBuilderService;

    /**
     * 将用户原始问题、召回片段和系统提示词组装为最终 Prompt 文本对。
     */
    @Override
    protected ChatProcessResult processCurrent(ModelChatContext context) {
        PromptContext promptContext = PromptContext.builder()
                .knowledgeChunks(context.getKnowledgeChunks())
                .systemPromptSnapshot(context.getSystemPrompt())
                .userPromptSnapshot(context.getUserPrompt())
                .runtimeVariables(context.getRuntimeVariables())
                .build();
        context.setPromptPair(promptBuilderService.build(promptContext));
        return ChatProcessResult.CONTINUE;
    }
}

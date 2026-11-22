package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.chat.model.bo.PromptPair;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 在模型调用边界把已准备的可信上下文编译为模型消息。
 *
 * <p>该对象不参与业务处理链，也不产生独立运行事件。</p>
 */
@Component
@RequiredArgsConstructor
public class ModelRequestFactory {

    private final PromptTemplateRenderer promptTemplateRenderer;

    /** 编译不包含自动知识检索的模型 Prompt。 */
    public PromptPair createDirectPrompt(ModelChatContext context) {
        String systemPrompt = promptTemplateRenderer.renderSystemPrompt(context.getSystemPrompt(), context.getRuntimeVariables()).trim();
        String userPrompt = Optional.ofNullable(context.getUserPrompt())
                .map(prompt -> promptTemplateRenderer.renderUserPrompt(prompt, context.getRuntimeVariables()))
                .orElseGet(() -> context.getRuntimeVariables().require(RuntimeVariableAccessors.USER_QUERY));
        return new PromptPair(userPrompt, systemPrompt);
    }
}

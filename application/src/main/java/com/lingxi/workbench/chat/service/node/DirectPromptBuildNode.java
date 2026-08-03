package com.lingxi.workbench.chat.service.node;

import com.lingxi.workbench.chat.common.enums.ChatProcessResult;
import com.lingxi.workbench.chat.model.bo.PromptPair;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.service.AbstractChatProcessNode;
import com.lingxi.workbench.chat.service.impl.PromptTemplateRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 为不依赖知识检索的 Agent 构建角色 Prompt。
 *
 * <p>Supervisor 只消费自身指令、会话历史和当前用户问题，不产生 knowledge.context，
 * 从结构上避免空召回规则干扰成员 Agent 委派。</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class DirectPromptBuildNode extends AbstractChatProcessNode {

    private final PromptTemplateRenderer promptTemplateRenderer;

    @Override
    protected ChatProcessResult processCurrent(ModelChatContext context) {
        String systemPrompt = promptTemplateRenderer.renderSystemPrompt(context.getSystemPrompt(), context.getRuntimeVariables()).trim();
        String userPrompt = Optional.ofNullable(context.getUserPrompt())
                .map(prompt -> promptTemplateRenderer.renderUserPrompt(prompt, context.getRuntimeVariables()))
                .orElseGet(() -> context.getRuntimeVariables().require(RuntimeVariableAccessors.USER_QUERY));
        context.setPromptPair(new PromptPair(userPrompt, systemPrompt));
        return ChatProcessResult.CONTINUE;
    }
}

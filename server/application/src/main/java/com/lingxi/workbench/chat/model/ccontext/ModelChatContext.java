package com.lingxi.workbench.chat.model.ccontext;

import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.chat.model.bo.PromptPair;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/** 模型调用边界使用的已发布 Prompt 与本轮可信运行变量。 */
@Getter
@Builder
public class ModelChatContext {

    private RuntimeVariables runtimeVariables;
    private PromptRuntimeSnapshot systemPrompt;
    private PromptRuntimeSnapshot userPrompt;
    @Setter
    private PromptPair promptPair;
}

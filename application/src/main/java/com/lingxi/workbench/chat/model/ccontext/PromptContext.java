package com.lingxi.workbench.chat.model.ccontext;

import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.knowledge.model.vo.KnowledgeSearchResultVO;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Prompt 构建阶段的只读输入。
 */
@Getter
@Builder
public class PromptContext {

    private List<KnowledgeSearchResultVO> knowledgeChunks;
    private PromptRuntimeSnapshot systemPromptSnapshot;
    private PromptRuntimeSnapshot userPromptSnapshot;
    private RuntimeVariables runtimeVariables;
    /**
     * Application 级记忆配置（含提示词版本 ID）。
     */
    private MemoryConfig memoryConfig;
}

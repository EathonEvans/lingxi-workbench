package com.lingxi.workbench.chat.model.ccontext;

import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.knowledge.model.vo.KnowledgeSearchResultVO;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Prompt 构建阶段的只读输入。
 *
 * <p>该对象只在处理链内部使用：检索节点负责准备知识片段，
 * Prompt 构建节点负责把这些结构化输入转换为模型消息。</p>
 */
@Getter
@Builder
public class PromptContext {

    /**
     * 经过 TopK、阈值和状态过滤后的知识库片段。
     */
    private List<KnowledgeSearchResultVO> knowledgeChunks;
    /**
     * Agent 已发布版本绑定的 SYSTEM PromptVersion 快照。
     */
    private PromptRuntimeSnapshot systemPromptSnapshot;
    /**
     * Agent 已发布版本绑定的 USER PromptVersion 快照。
     */
    private PromptRuntimeSnapshot userPromptSnapshot;
    /**
     * 本轮对话唯一的运行时变量容器。
     */
    private RuntimeVariables runtimeVariables;
}

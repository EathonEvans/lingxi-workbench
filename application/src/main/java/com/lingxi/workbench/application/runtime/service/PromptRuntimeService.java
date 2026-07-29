package com.lingxi.workbench.application.runtime.service;

import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;

/** 读取 AgentVersion 锁定的已发布 Prompt 运行快照。 */
public interface PromptRuntimeService {

    /**
     * 解析 AgentVersion 明确绑定的已发布 PromptVersion。
     *
     * @param promptVersionId Prompt 版本主键
     * @return 包含模板正文和变量声明的不可变运行快照
     */
    PromptRuntimeSnapshot resolvePublished(Long promptVersionId);
}

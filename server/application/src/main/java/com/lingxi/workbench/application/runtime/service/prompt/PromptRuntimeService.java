package com.lingxi.workbench.application.runtime.service.prompt;

import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;

import java.util.Optional;

/** 读取 AgentVersion 锁定的已发布 Prompt 运行快照。 */
public interface PromptRuntimeService {

    /**
     * 解析 AgentVersion 明确绑定的已发布 PromptVersion。
     *
     * @param promptVersionId Prompt 版本主键
     * @return 包含模板正文和变量声明的不可变运行快照
     */
    PromptRuntimeSnapshot resolvePublished(Long promptVersionId);

    /**
     * 按稳定 Prompt 编码解析最新已发布版本，供平台内置补充任务使用。
     *
     * @param promptCode Prompt 主记录稳定编码
     * @return 包含模板正文和变量声明的不可变运行快照；编码不存在或无已发布版本时返回空
     */
    Optional<PromptRuntimeSnapshot> resolveLatestPublishedByCode(String promptCode);
}

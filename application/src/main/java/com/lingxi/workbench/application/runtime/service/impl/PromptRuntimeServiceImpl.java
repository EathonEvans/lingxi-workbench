package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.entity.RuntimePromptVersionEntity;
import com.lingxi.workbench.application.runtime.repo.PromptRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.PromptRuntimeService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PromptRuntimeServiceImpl implements PromptRuntimeService {

    private final PromptRuntimeRepo repo;

    /**
     * 读取 AgentVersion 锁定的已发布 Prompt 正文和变量声明。
     * 仅查询状态为 PUBLISHED 的指定版本，不回退到其他版本或草稿。
     */
    @Override
    @Transactional(readOnly = true)
    public PromptRuntimeSnapshot resolvePublished(Long promptVersionId) {
        RuntimePromptVersionEntity version = repo.findPublishedVersion(promptVersionId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.CONFLICT,
                        "AgentVersion引用的PromptVersion不可用"));
        return new PromptRuntimeSnapshot(
                version.getId(),
                version.getPromptContent(),
                version.getVariables(),
                version.getRole());
    }
}

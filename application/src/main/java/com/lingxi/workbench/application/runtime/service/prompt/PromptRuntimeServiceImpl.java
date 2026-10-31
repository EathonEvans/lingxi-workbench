package com.lingxi.workbench.application.runtime.service.prompt;

import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.entity.RuntimePromptVersionEntity;
import com.lingxi.workbench.application.runtime.repo.PromptRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.prompt.PromptRuntimeService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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
        return toSnapshot(version);
    }

    /**
     * 按稳定编码读取平台内置补充任务的最新已发布 Prompt。
     * 编码不存在或没有已发布版本时返回空，由调用方决定降级行为。
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<PromptRuntimeSnapshot> resolveLatestPublishedByCode(String promptCode) {
        return repo.findLatestPublishedByPromptCode(promptCode).map(PromptRuntimeServiceImpl::toSnapshot);
    }

    private static PromptRuntimeSnapshot toSnapshot(RuntimePromptVersionEntity version) {
        return new PromptRuntimeSnapshot(
                version.getId(),
                version.getPromptContent(),
                version.getVariables(),
                version.getRole());
    }
}

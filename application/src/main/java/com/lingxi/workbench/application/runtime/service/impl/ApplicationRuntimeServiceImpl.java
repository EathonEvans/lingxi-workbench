package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeApplicationEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeApplicationVersionEntity;
import com.lingxi.workbench.application.runtime.model.vo.AvailableApplicationVO;
import com.lingxi.workbench.application.runtime.repo.ApplicationRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.ApplicationRuntimeService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ApplicationRuntimeServiceImpl implements ApplicationRuntimeService {

    private final ApplicationRuntimeRepo repo;

    /**
     * 查询应用端允许选择的 Application。
     *
     * <p>前端只持有稳定编码，不感知管理端发布产生的版本主键。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public List<AvailableApplicationVO> listAvailableApplications() {
        return repo.findAvailableApplications().stream()
                .map(application -> new AvailableApplicationVO(
                        application.getCode(),
                        application.getName(),
                        application.getDescription()))
                .toList();
    }

    /**
     * 按稳定编码解析当前已启用的 Application 版本。
     * 校验应用处于启用状态、已发布版本存在且定义快照满足运行不变量。
     */
    @Override
    @Transactional(readOnly = true)
    public AppRuntimeSnapshot loadRuntimeAppConfig(String applicationCode) {
        // 查询当前应用的激活版本
        RuntimeApplicationEntity application = repo.findActiveByCode(applicationCode)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "AI应用不存在或已停用"));

        if (Objects.isNull(application.getPublishedVersionId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AI应用尚未发布");
        }
        // 版本快照
        RuntimeApplicationVersionEntity version = repo.findVersion(application.getId(), application.getPublishedVersionId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "AI应用生效版本不存在"));

        if (Objects.isNull(version.getDefinitionSnapshot())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AI应用生效版本缺少定义快照");
        }
        try {
            return new AppRuntimeSnapshot(application.getId(), application.getCode(), version.getId(), version.getDefinitionSnapshot());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AI应用生效版本不可执行：" + exception.getMessage());
        }
    }
}

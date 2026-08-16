package com.lingxi.workbench.application.runtime.service.app;

import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeApplicationEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeApplicationVersionEntity;
import com.lingxi.workbench.application.runtime.model.vo.AvailableApplicationVO;
import com.lingxi.workbench.application.runtime.repo.ApplicationRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.app.ApplicationRuntimeService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
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
        List<RuntimeApplicationEntity> applications = repo.findAvailableApplications();
        List<Long> versionIds = applications.stream()
                .map(RuntimeApplicationEntity::getPublishedVersionId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, RuntimeApplicationVersionEntity> versions = repo.findVersionsByIds(versionIds).stream()
                .collect(Collectors.toMap(RuntimeApplicationVersionEntity::getId, Function.identity()));
        return applications.stream()
                .filter(application -> isExecutable(application, versions.get(application.getPublishedVersionId())))
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

        return toSnapshot(application, version, "AI应用生效版本");
    }

    @Override
    @Transactional(readOnly = true)
    public AppRuntimeSnapshot loadRuntimeAppVersion(Long applicationId, Long applicationVersionId) {
        RuntimeApplicationEntity application = repo.findById(applicationId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "AI应用不存在"));
        RuntimeApplicationVersionEntity version = repo.findVersion(applicationId, applicationVersionId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "AI应用冻结版本不存在"));
        return toSnapshot(application, version, "AI应用冻结版本");
    }

    /** 应用选择器只暴露当前运行时能够构造完整快照的正式版本。 */
    private boolean isExecutable(RuntimeApplicationEntity application, RuntimeApplicationVersionEntity version) {
        if (Objects.isNull(version)
                || !Objects.equals(version.getApplicationId(), application.getId())
                || Objects.isNull(version.getDefinitionSnapshot())) {
            log.warn("Application候选版本缺失或归属不一致，applicationId={}，versionId={}",
                    application.getId(), application.getPublishedVersionId());
            return false;
        }
        try {
            new AppRuntimeSnapshot(application.getId(), application.getCode(), version.getId(), version.getDefinitionSnapshot());
            return true;
        } catch (IllegalArgumentException exception) {
            log.warn("Application候选版本不可执行，applicationId={}，versionId={}，reason={}",
                    application.getId(), version.getId(), exception.getMessage());
            return false;
        }
    }

    /** 统一校验 ApplicationVersion 定义快照并转换为不可变运行对象。 */
    private AppRuntimeSnapshot toSnapshot(RuntimeApplicationEntity application, RuntimeApplicationVersionEntity version,
                                          String versionLabel) {
        if (Objects.isNull(version.getDefinitionSnapshot())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, versionLabel + "缺少定义快照");
        }
        try {
            return new AppRuntimeSnapshot(application.getId(), application.getCode(), version.getId(), version.getDefinitionSnapshot());
        } catch (IllegalArgumentException exception) {
            log.warn("{}不可执行，applicationId={}，versionId={}，reason={}",
                    versionLabel, application.getId(), version.getId(), exception.getMessage());
            throw new BusinessException(HttpStatusEnum.CONFLICT, versionLabel + "不可执行：" + exception.getMessage());
        }
    }
}

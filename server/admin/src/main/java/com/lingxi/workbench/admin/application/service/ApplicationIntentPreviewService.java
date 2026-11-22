package com.lingxi.workbench.admin.application.service;

import com.lingxi.workbench.admin.application.gateway.ApplicationIntentPreviewGateway;
import com.lingxi.workbench.admin.application.model.entity.AiApplicationEntity;
import com.lingxi.workbench.admin.application.model.request.PreviewApplicationIntentRequest;
import com.lingxi.workbench.admin.application.model.vo.ApplicationIntentPreviewVO;
import com.lingxi.workbench.admin.application.model.vo.ApplicationValidationIssueVO;
import com.lingxi.workbench.admin.application.repo.AiApplicationRepo;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationContextPolicy;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.stream.Collectors;

import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;

/** 解析当前页面草稿并代理一次只读意图测试，不保存草稿、不发布版本。 */
@Service
@RequiredArgsConstructor
public class ApplicationIntentPreviewService {

    private final AiApplicationRepo applicationRepo;
    private final ApplicationDraftValidator draftValidator;
    private final ApplicationIntentPreviewGateway previewGateway;

    @Transactional(readOnly = true)
    public ApplicationIntentPreviewVO preview(Long applicationId, PreviewApplicationIntentRequest request) {
        AiApplicationEntity application = applicationRepo.findById(applicationId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "Application不存在"));
        ApplicationDefinition draft = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .collaboration(request.getCollaboration())
                .intentRouting(request.getIntentRouting())
                .memoryConf(Objects.requireNonNullElseGet(request.getMemory(), MemoryConfig::disabled))
                .contextPolicy(Objects.requireNonNullElseGet(
                        request.getContextPolicy(), ApplicationContextPolicy::defaults))
                .capabilityCatalog(Objects.requireNonNullElseGet(
                        request.getCapabilityCatalog(), CapabilityCatalogSnapshot::empty))
                .runtimePolicy(Objects.requireNonNullElseGet(
                        request.getRuntimePolicy(), ApplicationRuntimePolicy::defaults))
                .build();
        ApplicationDefinitionResolution resolution = draftValidator.resolveForPublish(draft, application.getDomainId());
        if (!resolution.valid()) {
            String message = resolution.issues().stream()
                    .map(ApplicationValidationIssueVO::message)
                    .collect(Collectors.joining("；"));
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Application草稿不可测试：" + message);
        }
        return previewGateway.preview(
                application.getId(), application.getCode(), request.getQuery().trim(), resolution.definition());
    }
}

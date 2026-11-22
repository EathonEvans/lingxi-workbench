package com.lingxi.workbench.admin.application.service.impl;

import com.lingxi.workbench.admin.application.model.converter.ApplicationConverter;
import com.lingxi.workbench.admin.application.model.entity.AiApplicationEntity;
import com.lingxi.workbench.admin.application.model.entity.AiApplicationVersionEntity;
import com.lingxi.workbench.admin.application.model.entity.AiApplicationDraftEntity;
import com.lingxi.workbench.admin.application.model.request.PublishApplicationRequest;
import com.lingxi.workbench.admin.application.service.ApplicationDefinitionResolution;
import com.lingxi.workbench.admin.application.repo.AiApplicationDraftRepo;
import com.lingxi.workbench.admin.application.repo.AiApplicationRepo;
import com.lingxi.workbench.admin.application.repo.AiApplicationVersionRepo;
import com.lingxi.workbench.admin.application.service.ApplicationDraftValidator;
import com.lingxi.workbench.admin.application.service.ApplicationSnapshotChecksum;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.domain.service.DomainService;
import com.lingxi.workbench.admin.evaluation.service.EvaluationService;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;

class ApplicationServiceImplEvaluationGateTest {

    @Test
    void shouldRequirePassingProductionEvaluationBeforeActivatingRoutedVersion() {
        AiApplicationRepo applicationRepo = mock(AiApplicationRepo.class);
        AiApplicationVersionRepo versionRepo = mock(AiApplicationVersionRepo.class);
        EvaluationService evaluationService = mock(EvaluationService.class);
        ApplicationServiceImpl service = service(applicationRepo, versionRepo, evaluationService);
        AiApplicationEntity application = new AiApplicationEntity();
        application.setId(10L);
        application.setCode("career_assistant");
        application.setStatus(EnableStatus.ACTIVE);
        AiApplicationVersionEntity version = version(10L, 20L, true);
        when(applicationRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(application));
        when(versionRepo.findByApplicationIdAndId(10L, 20L)).thenReturn(Optional.of(version));

        service.activateVersion(10L, 20L, 7L);

        verify(evaluationService).requireProductionGatePassed(10L, 20L);
        verify(applicationRepo).save(application);
    }

    @Test
    void shouldPublishRoutedVersionWithoutActivatingItBeforeEvaluation() {
        AiApplicationRepo applicationRepo = mock(AiApplicationRepo.class);
        AiApplicationDraftRepo draftRepo = mock(AiApplicationDraftRepo.class);
        AiApplicationVersionRepo versionRepo = mock(AiApplicationVersionRepo.class);
        ApplicationDraftValidator validator = mock(ApplicationDraftValidator.class);
        ApplicationSnapshotChecksum checksum = mock(ApplicationSnapshotChecksum.class);
        ApplicationConverter converter = mock(ApplicationConverter.class);
        EvaluationService evaluationService = mock(EvaluationService.class);
        ApplicationServiceImpl service = new ApplicationServiceImpl(
                applicationRepo, draftRepo, versionRepo, validator, checksum, converter,
                mock(AuditService.class), mock(DomainService.class), evaluationService);
        AiApplicationEntity application = new AiApplicationEntity();
        application.setId(10L);
        application.setCode("career_assistant");
        application.setName("个人求职助手");
        AiApplicationDraftEntity draft = new AiApplicationDraftEntity();
        draft.setId(11L);
        draft.setApplicationId(10L);
        draft.setRevision(3L);
        ApplicationDefinition definition = version(10L, 20L, true).getDefinitionSnapshot();
        PublishApplicationRequest request = new PublishApplicationRequest();
        request.setExpectedRevision(3L);
        when(applicationRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(application));
        when(draftRepo.findByApplicationIdForUpdate(10L)).thenReturn(Optional.of(draft));
        when(validator.resolveForPublish(draft.getDefinition(), application.getDomainId()))
                .thenReturn(new ApplicationDefinitionResolution(definition, List.of()));
        when(checksum.calculate(definition, application.getName(), application.getAvatar())).thenReturn("checksum");
        when(versionRepo.nextVersionNo(10L)).thenReturn(8);
        doAnswer(invocation -> {
            invocation.getArgument(0, AiApplicationVersionEntity.class).setId(20L);
            return null;
        }).when(versionRepo).insert(org.mockito.ArgumentMatchers.any(AiApplicationVersionEntity.class));

        service.publish(10L, request, 7L);

        verify(applicationRepo, never()).save(application);
        verify(draftRepo).updatePublicationBase(11L, 20L, 7L, 3L);
    }

    private static ApplicationServiceImpl service(AiApplicationRepo applicationRepo,
                                                   AiApplicationVersionRepo versionRepo,
                                                   EvaluationService evaluationService) {
        return new ApplicationServiceImpl(
                applicationRepo,
                mock(AiApplicationDraftRepo.class),
                versionRepo,
                mock(ApplicationDraftValidator.class),
                mock(ApplicationSnapshotChecksum.class),
                mock(ApplicationConverter.class),
                mock(AuditService.class),
                mock(DomainService.class),
                evaluationService);
    }

    private static AiApplicationVersionEntity version(Long applicationId, Long versionId, boolean routingEnabled) {
        AiApplicationVersionEntity version = new AiApplicationVersionEntity();
        version.setId(versionId);
        version.setApplicationId(applicationId);
        version.setVersionNo(8);
        version.setSchemaVersion(APPLICATION_SCHEMA_VERSION);
        version.setName("个人求职助手");
        version.setDefinitionSnapshot(ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .intentRouting(ApplicationIntentBinding.builder()
                        .enabled(routingEnabled)
                        .intentTreeId(30L)
                        .intentTreeVersionId(40L)
                        .mappings(List.of())
                        .build())
                .build());
        return version;
    }
}

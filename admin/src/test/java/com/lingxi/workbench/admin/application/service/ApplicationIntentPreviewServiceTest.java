package com.lingxi.workbench.admin.application.service;

import com.lingxi.workbench.admin.application.gateway.ApplicationIntentPreviewGateway;
import com.lingxi.workbench.admin.application.model.entity.AiApplicationEntity;
import com.lingxi.workbench.admin.application.model.request.PreviewApplicationIntentRequest;
import com.lingxi.workbench.admin.application.model.vo.ApplicationIntentPreviewVO;
import com.lingxi.workbench.admin.application.model.vo.ApplicationValidationIssueVO;
import com.lingxi.workbench.admin.application.repo.AiApplicationRepo;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;

class ApplicationIntentPreviewServiceTest {

    private final AiApplicationRepo applicationRepo = mock(AiApplicationRepo.class);
    private final ApplicationDraftValidator draftValidator = mock(ApplicationDraftValidator.class);
    private final ApplicationIntentPreviewGateway gateway = mock(ApplicationIntentPreviewGateway.class);
    private final ApplicationIntentPreviewService service = new ApplicationIntentPreviewService(
            applicationRepo, draftValidator, gateway);

    @Test
    void shouldResolveUnsavedDraftBeforeCallingApplicationRuntime() {
        AiApplicationEntity application = application();
        PreviewApplicationIntentRequest request = request();
        ApplicationDefinition resolved = ApplicationDefinition.builder().schemaVersion(APPLICATION_SCHEMA_VERSION).build();
        ApplicationIntentPreviewVO expected = new ApplicationIntentPreviewVO();
        when(applicationRepo.findById(10L)).thenReturn(Optional.of(application));
        when(draftValidator.resolveForPublish(any(), any())).thenReturn(
                new ApplicationDefinitionResolution(resolved, List.of()));
        when(gateway.preview(10L, "refund-app", "我要退款", resolved)).thenReturn(expected);

        ApplicationIntentPreviewVO result = service.preview(10L, request);

        assertThat(result).isSameAs(expected);
        verify(draftValidator).resolveForPublish(any(ApplicationDefinition.class), any());
        verify(gateway).preview(10L, "refund-app", "我要退款", resolved);
    }

    @Test
    void shouldRejectInvalidDraftBeforeCallingApplicationRuntime() {
        when(applicationRepo.findById(10L)).thenReturn(Optional.of(application()));
        when(draftValidator.resolveForPublish(any(), any())).thenReturn(
                new ApplicationDefinitionResolution(null, List.of(
                        new ApplicationValidationIssueVO("INTENT_TREE_REQUIRED", "definition.intentRouting", "请选择意图树"))));

        assertThatThrownBy(() -> service.preview(10L, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Application草稿不可测试：请选择意图树");
        verify(gateway, never()).preview(any(), any(), any(), any());
    }

    private static AiApplicationEntity application() {
        AiApplicationEntity application = new AiApplicationEntity();
        application.setId(10L);
        application.setCode("refund-app");
        application.setDomainId(20L);
        return application;
    }

    private static PreviewApplicationIntentRequest request() {
        PreviewApplicationIntentRequest request = new PreviewApplicationIntentRequest();
        request.setQuery("  我要退款  ");
        return request;
    }
}

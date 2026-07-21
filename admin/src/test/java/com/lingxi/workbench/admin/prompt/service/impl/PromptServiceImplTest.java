package com.lingxi.workbench.admin.prompt.service.impl;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableType;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVersionStatus;
import com.lingxi.workbench.admin.prompt.model.converter.PromptConverter;
import com.lingxi.workbench.admin.prompt.model.entity.PromptEntity;
import com.lingxi.workbench.admin.prompt.model.entity.PromptVariableDefinition;
import com.lingxi.workbench.admin.prompt.model.entity.PromptVersionEntity;
import com.lingxi.workbench.admin.prompt.model.request.PromptVariableRequest;
import com.lingxi.workbench.admin.prompt.repo.PromptRepo;
import com.lingxi.workbench.admin.prompt.repo.PromptVersionRepo;
import com.lingxi.workbench.framework.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptServiceImplTest {

    @Mock private PromptRepo promptRepo;
    @Mock private PromptVersionRepo versionRepo;
    @Mock private PromptConverter promptConverter;
    @Mock private AuditService auditService;
    @Mock private PromptTemplateProcessor templateProcessor;

    @InjectMocks private PromptServiceImpl service;

    @Test
    void shouldPublishDraftWithoutChangingItsContent() {
        PromptEntity prompt = new PromptEntity();
        prompt.setId(11L);
        prompt.setCode("general_assistant");
        prompt.setStatus(EnableStatus.ACTIVE);

        PromptVersionEntity draft = new PromptVersionEntity();
        draft.setId(21L);
        draft.setPromptId(11L);
        draft.setVersionNo(2);
        draft.setPromptContent("你是{{agent.name}}");
        PromptVariableRequest variableRequest = variable("agent.name", true);
        draft.setVariables(List.of(definition(variableRequest)));
        draft.setStatus(PromptVersionStatus.DRAFT);

        when(promptRepo.findById(11L)).thenReturn(Optional.of(prompt));
        when(versionRepo.findDraft(11L)).thenReturn(Optional.of(draft));
        when(promptConverter.toVariableRequests(draft.getVariables()))
                .thenReturn(List.of(variableRequest));

        service.publish(11L, 1L);

        assertEquals(PromptVersionStatus.PUBLISHED, draft.getStatus());
        assertNotNull(draft.getPublishedAt());
        assertEquals(21L, prompt.getPublishedVersionId());
        assertEquals("你是{{agent.name}}", draft.getPromptContent());
        verify(versionRepo).save(draft);
        verify(promptRepo).save(prompt);
        verify(templateProcessor).validate(draft.getPromptContent(), List.of(variableRequest));
        verify(auditService).record(
                "PROMPT", "PROMPT", 11L, 1L, "PROMPT_PUBLISH",
                "Prompt编码=general_assistant，发布版本=V2");
    }

    private static PromptVariableRequest variable(
            String name,
            boolean required) {
        return new PromptVariableRequest(
                name,
                PromptVariableType.STRING,
                required,
                name,
                null);
    }

    private static PromptVariableDefinition definition(PromptVariableRequest request) {
        return new PromptVariableDefinition(
                request.name(),
                request.type(),
                request.required(),
                request.description(),
                request.example());
    }
}

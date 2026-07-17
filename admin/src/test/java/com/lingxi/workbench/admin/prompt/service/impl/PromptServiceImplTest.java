package com.lingxi.workbench.admin.prompt.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVersionStatus;
import com.lingxi.workbench.admin.prompt.model.converter.PromptConverter;
import com.lingxi.workbench.admin.prompt.model.entity.PromptEntity;
import com.lingxi.workbench.admin.prompt.model.entity.PromptVersionEntity;
import com.lingxi.workbench.admin.prompt.model.request.PreviewPromptRequest;
import com.lingxi.workbench.admin.prompt.model.request.PromptVariableRequest;
import com.lingxi.workbench.admin.prompt.model.vo.PreviewPromptVO;
import com.lingxi.workbench.admin.prompt.repo.PromptRepo;
import com.lingxi.workbench.admin.prompt.repo.PromptVersionRepo;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptServiceImplTest {

    @Mock private PromptRepo promptRepo;
    @Mock private PromptVersionRepo versionRepo;
    @Mock private PromptConverter promptConverter;
    @Mock private AuditService auditService;

    @InjectMocks private PromptServiceImpl service;

    @Test
    void shouldRenderDeclaredVariables() {
        PromptVariableRequest agentName =
                new PromptVariableRequest("agentName", true, "Agent 名称", null);
        PromptVariableRequest language =
                new PromptVariableRequest("language", false, "回答语言", "中文");

        PreviewPromptVO result = service.preview(new PreviewPromptRequest(
                "你是 {{agentName}}，请使用 {{ language }}回答。",
                List.of(agentName, language),
                Map.of("agentName", "灵析助手")));

        assertEquals("你是 灵析助手，请使用 中文回答。", result.renderedPrompt());
    }

    @Test
    void shouldRejectUndeclaredVariable() {
        PreviewPromptRequest request = new PreviewPromptRequest(
                "请使用 {{language}} 回答。", List.of(), Map.of());

        assertThrows(BusinessException.class, () -> service.preview(request));
    }

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
        draft.setPromptContent("你是 {{agentName}}");
        draft.setVariables(JSON.toJSONString(List.of(
                new PromptVariableRequest("agentName", true, "Agent 名称", null))));
        draft.setStatus(PromptVersionStatus.DRAFT);

        when(promptRepo.findById(11L)).thenReturn(Optional.of(prompt));
        when(versionRepo.findDraft(11L)).thenReturn(Optional.of(draft));

        service.publish(11L, 1L);

        assertEquals(PromptVersionStatus.PUBLISHED, draft.getStatus());
        assertNotNull(draft.getPublishedAt());
        assertEquals(21L, prompt.getPublishedVersionId());
        assertEquals("你是 {{agentName}}", draft.getPromptContent());
        verify(versionRepo).save(draft);
        verify(promptRepo).save(prompt);
        verify(auditService).record(
                "PROMPT", "PROMPT", 11L, 1L, "PROMPT_PUBLISH",
                "Prompt编码=general_assistant，发布版本=V2");
    }
}

package com.lingxi.workbench.admin.workflow.service.impl;

import com.lingxi.workbench.admin.workflow.model.converter.WorkflowConverter;
import com.lingxi.workbench.admin.workflow.model.entity.WorkflowDraftEntity;
import com.lingxi.workbench.admin.workflow.model.entity.WorkflowEntity;
import com.lingxi.workbench.admin.workflow.model.request.CreateWorkflowRequest;
import com.lingxi.workbench.admin.workflow.repo.WorkflowDraftRepo;
import com.lingxi.workbench.admin.workflow.repo.WorkflowRepo;
import com.lingxi.workbench.admin.workflow.repo.WorkflowVersionRepo;
import com.lingxi.workbench.admin.workflow.service.WorkflowDraftResolver;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.service.WorkflowDefinitionValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowServiceImplTest {

    @Test
    void shouldCreateValidInitialDraft() {
        WorkflowRepo workflowRepo = mock(WorkflowRepo.class);
        WorkflowDraftRepo draftRepo = mock(WorkflowDraftRepo.class);
        WorkflowVersionRepo versionRepo = mock(WorkflowVersionRepo.class);
        AtomicReference<WorkflowEntity> savedWorkflow = new AtomicReference<>();
        AtomicReference<WorkflowDraftEntity> savedDraft = new AtomicReference<>();
        doAnswer(invocation -> {
            WorkflowEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            savedWorkflow.set(entity);
            return null;
        }).when(workflowRepo).save(any(WorkflowEntity.class));
        doAnswer(invocation -> {
            WorkflowDraftEntity entity = invocation.getArgument(0);
            entity.setId(200L);
            savedDraft.set(entity);
            return null;
        }).when(draftRepo).save(any(WorkflowDraftEntity.class));
        when(workflowRepo.findById(100L)).thenAnswer(invocation -> Optional.of(savedWorkflow.get()));
        when(draftRepo.findByWorkflowId(100L)).thenAnswer(invocation -> Optional.of(savedDraft.get()));
        when(versionRepo.findByWorkflowId(100L)).thenReturn(List.of());

        WorkflowServiceImpl service = new WorkflowServiceImpl(workflowRepo, draftRepo, versionRepo,
                new WorkflowConverter(), mock(WorkflowDraftResolver.class));
        CreateWorkflowRequest request = new CreateWorkflowRequest();
        request.setName("回归测试 Workflow");
        request.setCode("regression_workflow");

        service.create(request, 1L);

        WorkflowDefinition definition = savedDraft.get().getDefinition();
        new WorkflowDefinitionValidator().validateDraft(definition);
        assertThat(definition.nodes()).extracting(node -> node.id()).containsExactly("start", "end");
        assertThat(savedDraft.get().getCanvasState().nodePositions()).containsOnlyKeys("start", "end");
    }
}

package com.lingxi.workbench.admin.workflow.service;

import com.lingxi.workbench.admin.agent.service.AgentReferenceService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.exception.WorkflowDefinitionException;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.config.AgentNodeConfig;
import com.lingxi.workbench.workflow.service.WorkflowDefinitionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 校验独立 Workflow 草稿并在发布时锁定所有 AgentVersion。 */
@Component
@RequiredArgsConstructor
public class WorkflowDraftResolver {

    private final AgentReferenceService agentReferenceService;
    private final WorkflowDefinitionValidator validator = new WorkflowDefinitionValidator();

    public WorkflowDefinition resolveForPublish(WorkflowDefinition draft) {
        validateDraft(draft);
        List<WorkflowNodeDefinition> resolvedNodes = new ArrayList<>(draft.nodes().size());
        for (WorkflowNodeDefinition node : draft.nodes()) {
            if (!(node.config() instanceof AgentNodeConfig config)) {
                resolvedNodes.add(node);
                continue;
            }
            resolvedNodes.add(new WorkflowNodeDefinition(
                    node.id(), node.name(), node.type(), resolveAgent(node.id(), config)));
        }
        WorkflowDefinition resolved = new WorkflowDefinition(
                draft.schemaVersion(), draft.code(), resolvedNodes, draft.edges());
        try {
            validator.validate(resolved);
        } catch (WorkflowDefinitionException exception) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Workflow发布校验失败：" + exception.getMessage());
        }
        return resolved;
    }

    private void validateDraft(WorkflowDefinition draft) {
        try {
            validator.validateDraft(draft);
        } catch (WorkflowDefinitionException exception) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Workflow草稿结构无效：" + exception.getMessage());
        }
    }

    private AgentNodeConfig resolveAgent(String nodeId, AgentNodeConfig config) {
        AgentReferenceService.AgentReference agent = agentReferenceService.findAgent(config.agentId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.BAD_REQUEST, "节点" + nodeId + "引用的Agent不存在"));
        if (!agent.active()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "节点" + nodeId + "引用的Agent已停用");
        }
        Long versionId = Objects.requireNonNullElse(config.agentVersionId(), agent.publishedVersionId());
        if (Objects.isNull(versionId) || agentReferenceService.findVersion(config.agentId(), versionId).isEmpty()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "节点" + nodeId + "没有可用的Agent正式版本");
        }
        return new AgentNodeConfig(config.agentId(), versionId, config.inputMapping(), config.outputMapping());
    }
}

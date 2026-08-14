package com.lingxi.workbench.admin.workflow.service;

import com.lingxi.workbench.admin.agent.service.AgentReferenceService;
import com.lingxi.workbench.admin.intent.service.IntentTreeService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.exception.WorkflowDefinitionException;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.config.AgentNodeConfig;
import com.lingxi.workbench.workflow.model.config.IntentClassifierNodeConfig;
import com.lingxi.workbench.workflow.service.WorkflowDefinitionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.lingxi.workbench.infra.ai.agent.common.constants.AgentSchemaConstants.AGENT_SCHEMA_VERSION;

/**
 * 校验独立 Workflow 草稿并在发布时锁定所有外部资源版本。
 */
@Component
@RequiredArgsConstructor
public class WorkflowDraftResolver {

    private final AgentReferenceService agentReferenceService;
    private final IntentTreeService intentTreeService;
    private final WorkflowDefinitionValidator validator = new WorkflowDefinitionValidator();

    public WorkflowDefinition resolveForPublish(WorkflowDefinition draft) {
        validateDraft(draft);
        List<WorkflowNodeDefinition> resolvedNodes = new ArrayList<>(draft.nodes().size());
        for (WorkflowNodeDefinition node : draft.nodes()) {
            resolvedNodes.add(resolveNode(node));
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

    private WorkflowNodeDefinition resolveNode(WorkflowNodeDefinition node) {
        if (node.config() instanceof AgentNodeConfig config) {
            return new WorkflowNodeDefinition(node.id(), node.name(), node.type(), resolveAgent(node.id(), config));
        }
        if (node.config() instanceof IntentClassifierNodeConfig config) {
            return new WorkflowNodeDefinition(node.id(), node.name(), node.type(), resolveIntentTree(node.id(), config));
        }
        return node;
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
        if (agent.identity().role() != com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole.SPECIALIST) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "节点" + nodeId + "只能引用领域专家Agent");
        }
        Long versionId = Optional.ofNullable(config.agentVersionId()).orElse(agent.publishedVersionId());
        AgentReferenceService.AgentVersionReference version = Objects.isNull(versionId)
                ? null
                : agentReferenceService.findVersion(config.agentId(), versionId).orElse(null);
        if (Objects.isNull(version)
                || version.schemaVersion() != AGENT_SCHEMA_VERSION
                || version.role() != agent.identity().role()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "节点" + nodeId + "没有可用的Agent正式版本");
        }
        return new AgentNodeConfig(config.agentId(), version.id(), config.inputMapping(), config.outputMapping());
    }

    private IntentClassifierNodeConfig resolveIntentTree(String nodeId, IntentClassifierNodeConfig config) {
        IntentTreeService.IntentTreeReference intentTree = intentTreeService.findReference(config.intentTreeId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.BAD_REQUEST, "节点" + nodeId + "引用的意图树不存在"));
        if (!intentTree.active()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "节点" + nodeId + "引用的意图树已停用");
        }
        Long versionId = Optional.ofNullable(config.intentTreeVersionId()).orElse(intentTree.publishedVersionId());
        if (Objects.isNull(versionId) || intentTreeService.findVersion(config.intentTreeId(), versionId).isEmpty()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "节点" + nodeId + "没有可用的意图树正式版本");
        }
        return new IntentClassifierNodeConfig(config.intentTreeId(), versionId, config.queryVariable(), config.outputMapping());
    }
}

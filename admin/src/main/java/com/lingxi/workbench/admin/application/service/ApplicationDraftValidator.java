package com.lingxi.workbench.admin.application.service;

import com.lingxi.workbench.admin.application.model.vo.ApplicationValidationIssueVO;
import com.lingxi.workbench.workflow.exception.WorkflowDefinitionException;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeType;
import com.lingxi.workbench.workflow.model.config.AgentNodeConfig;
import com.lingxi.workbench.workflow.service.WorkflowDefinitionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Application 发布校验与引用解析。
 *
 * <p>草稿允许 AgentVersion 为空；发布快照必须解析并锁定不可变版本。</p>
 */
@Component
@RequiredArgsConstructor
public class ApplicationDraftValidator {
    private static final int APPLICATION_SCHEMA_VERSION = 1;

    private final ApplicationAgentReferenceGateway agentReferenceGateway;
    private final WorkflowDefinitionValidator workflowValidator =
            new WorkflowDefinitionValidator();

    /** 解析草稿中 Agent 节点的版本引用并校验 Workflow 结构合法性，返回已锁定版本的定义。 */
    public ApplicationDefinitionResolution resolveForPublish(
            ApplicationDefinition draftDefinition) {
        List<ApplicationValidationIssueVO> issues = new ArrayList<>();
        if (draftDefinition == null) {
            issues.add(issue("DEFINITION_REQUIRED", "definition", "应用定义不能为空"));
            return new ApplicationDefinitionResolution(null, issues);
        }
        if (draftDefinition.schemaVersion() != APPLICATION_SCHEMA_VERSION) {
            issues.add(issue(
                    "SCHEMA_VERSION_UNSUPPORTED",
                    "definition",
                    "不支持的Application schemaVersion: " + draftDefinition.schemaVersion()));
            return new ApplicationDefinitionResolution(null, issues);
        }

        WorkflowDefinition workflow = draftDefinition.workflow();
        try {
            workflowValidator.validateDraft(workflow);
        } catch (WorkflowDefinitionException exception) {
            issues.add(issue("WORKFLOW_INVALID", "definition.workflow", exception.getMessage()));
            return new ApplicationDefinitionResolution(null, issues);
        }

        long agentNodeCount = workflow.nodes().stream()
                .filter(node -> node.type() == WorkflowNodeType.AGENT)
                .count();
        if (agentNodeCount == 0) {
            issues.add(issue(
                    "AGENT_NODE_REQUIRED",
                    "definition.workflow",
                    "Application至少需要配置一个Agent节点"));
        }

        List<WorkflowNodeDefinition> resolvedNodes = new ArrayList<>(workflow.nodes().size());
        for (WorkflowNodeDefinition node : workflow.nodes()) {
            if (!(node.config() instanceof AgentNodeConfig config)) {
                resolvedNodes.add(node);
                continue;
            }
            AgentNodeConfig resolved = resolveAgentNode(node.id(), config, issues);
            resolvedNodes.add(new WorkflowNodeDefinition(
                    node.id(), node.name(), node.type(), resolved));
        }

        if (!issues.isEmpty()) {
            return new ApplicationDefinitionResolution(null, issues);
        }
        WorkflowDefinition resolvedWorkflow = new WorkflowDefinition(
                workflow.schemaVersion(),
                workflow.code(),
                resolvedNodes,
                workflow.edges());
        try {
            workflowValidator.validate(resolvedWorkflow);
        } catch (WorkflowDefinitionException exception) {
            issues.add(issue("WORKFLOW_INVALID", "definition.workflow", exception.getMessage()));
            return new ApplicationDefinitionResolution(null, issues);
        }
        return new ApplicationDefinitionResolution(
                new ApplicationDefinition(draftDefinition.schemaVersion(), resolvedWorkflow),
                issues);
    }

    private AgentNodeConfig resolveAgentNode(
            String nodeId,
            AgentNodeConfig config,
            List<ApplicationValidationIssueVO> issues) {
        Long agentId = parseId(config.agentId(), nodeId, "agentId", issues);
        if (agentId == null) {
            return config;
        }
        ApplicationAgentReferenceGateway.AgentReference agent =
                agentReferenceGateway.findAgent(agentId).orElse(null);
        if (agent == null) {
            issues.add(issue("AGENT_NOT_FOUND", nodeId, "引用的Agent不存在"));
            return config;
        }
        if (!agent.active()) {
            issues.add(issue("AGENT_DISABLED", nodeId, "引用的Agent已停用"));
            return config;
        }

        Long versionId;
        if (config.agentVersionId() == null || config.agentVersionId().isBlank()) {
            versionId = agent.publishedVersionId();
            if (versionId == null) {
                issues.add(issue(
                        "AGENT_UNPUBLISHED",
                        nodeId,
                        "引用的Agent尚无生效版本"));
                return config;
            }
        } else {
            versionId = parseId(
                    config.agentVersionId(), nodeId, "agentVersionId", issues);
            if (versionId == null) {
                return config;
            }
        }

        Long resolvedVersionId =
                agentReferenceGateway.findVersion(agentId, versionId).orElse(null);
        if (resolvedVersionId == null) {
            issues.add(issue(
                    "AGENT_VERSION_NOT_FOUND",
                    nodeId,
                    "Agent版本不存在或不属于所选Agent"));
            return config;
        }
        return new AgentNodeConfig(
                String.valueOf(agentId),
                String.valueOf(resolvedVersionId),
                config.inputMapping(),
                config.outputMapping());
    }

    private static Long parseId(
            String raw,
            String nodeId,
            String field,
            List<ApplicationValidationIssueVO> issues) {
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException exception) {
            issues.add(issue(
                    "INVALID_REFERENCE_ID",
                    nodeId,
                    field + "不是有效ID"));
            return null;
        }
    }

    private static ApplicationValidationIssueVO issue(
            String code, String field, String message) {
        return new ApplicationValidationIssueVO(code, field, message);
    }
}

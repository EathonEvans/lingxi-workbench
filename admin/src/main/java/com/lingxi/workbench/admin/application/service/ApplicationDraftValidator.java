package com.lingxi.workbench.admin.application.service;

import com.lingxi.workbench.admin.agent.common.enums.AgentType;
import com.lingxi.workbench.admin.application.model.vo.ApplicationValidationIssueVO;
import com.lingxi.workbench.workflow.exception.WorkflowDefinitionException;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.AgentCollaborationDefinition;
import com.lingxi.workbench.workflow.model.AgentCollaborationLimits;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeType;
import com.lingxi.workbench.workflow.model.config.AgentNodeConfig;
import com.lingxi.workbench.workflow.service.WorkflowDefinitionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;
import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.MAX_DELEGATIONS;
import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.MAX_MEMBER_AGENTS;
import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.MIN_DELEGATIONS;
import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.MIN_MEMBER_AGENTS;
import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.SUPERVISOR_MAX_DEPTH;

/**
 * Application 发布校验与引用解析。
 *
 * <p>草稿允许 AgentVersion 为空；发布快照必须解析并锁定不可变版本。</p>
 */
@Component
@RequiredArgsConstructor
public class ApplicationDraftValidator {
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
        if (draftDefinition.schemaVersion() < 1 || draftDefinition.schemaVersion() > APPLICATION_SCHEMA_VERSION) {
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
        AgentCollaborationDefinition collaboration = resolveCollaboration(draftDefinition.collaboration(), resolvedWorkflow, issues);
        if (!issues.isEmpty()) {
            return new ApplicationDefinitionResolution(null, issues);
        }
        return new ApplicationDefinitionResolution(new ApplicationDefinition(APPLICATION_SCHEMA_VERSION, resolvedWorkflow, collaboration), issues);
    }

    /**
     * 将草稿中的稳定 Agent 主键解析成不可变版本引用，并验证 V1 Supervisor 拓扑。
     */
    private AgentCollaborationDefinition resolveCollaboration(AgentCollaborationDefinition draft, WorkflowDefinition workflow, List<ApplicationValidationIssueVO> issues) {
        if (Objects.isNull(draft) || !draft.enabled()) {
            return null;
        }
        if (Objects.isNull(draft.supervisor()) || Objects.isNull(draft.supervisor().agentId())) {
            issues.add(issue("SUPERVISOR_REQUIRED", "definition.collaboration.supervisor", "协同模式必须选择Supervisor Agent"));
            return null;
        }
        AgentNodeConfig supervisorNode = resolveSupervisorNode(draft.supervisor().agentId(), workflow, issues);

        if (draft.members().isEmpty() || draft.members().size() > MAX_MEMBER_AGENTS) {
            issues.add(issue("MEMBER_COUNT_INVALID", "definition.collaboration.members", "成员Agent数量必须在" + MIN_MEMBER_AGENTS + "到" + MAX_MEMBER_AGENTS + "之间"));
        }
        AgentCollaborationLimits limits = resolveLimits(draft.limits(), issues);
        ApplicationAgentReference supervisor = Objects.isNull(supervisorNode) ? null : resolveCollaborationAgent(draft.supervisor(), supervisorNode.agentVersionId(), AgentType.SUPERVISOR, "definition.collaboration.supervisor", issues);

        Set<Long> memberIds = new HashSet<>();
        List<ApplicationAgentReference> members = new ArrayList<>(draft.members().size());
        for (int index = 0; index < draft.members().size(); index++) {
            ApplicationAgentReference member = draft.members().get(index);
            String field = "definition.collaboration.members[" + index + "]";
            if (Objects.isNull(member) || Objects.isNull(member.agentId())) {
                issues.add(issue("MEMBER_REQUIRED", field, "成员Agent不能为空"));
                continue;
            }
            if (!memberIds.add(member.agentId())) {
                issues.add(issue("MEMBER_DUPLICATED", field, "成员Agent不能重复"));
                continue;
            }
            if (Objects.equals(member.agentId(), draft.supervisor().agentId())) {
                issues.add(issue("SUPERVISOR_AS_MEMBER", field, "Supervisor不能同时作为成员Agent"));
                continue;
            }
            ApplicationAgentReference resolved = resolveCollaborationAgent(member, null, AgentType.RAG_CHAT, field, issues);
            if (Objects.nonNull(resolved)) {
                members.add(resolved);
            }
        }
        if (!issues.isEmpty() || Objects.isNull(supervisor) || Objects.isNull(limits)) {
            return null;
        }
        return new AgentCollaborationDefinition(true, supervisor, members, limits);
    }

    private AgentNodeConfig resolveSupervisorNode(Long supervisorAgentId, WorkflowDefinition workflow, List<ApplicationValidationIssueVO> issues) {
        List<AgentNodeConfig> agentNodes = workflow.nodes().stream()
                .filter(node -> node.config() instanceof AgentNodeConfig)
                .map(node -> (AgentNodeConfig) node.config())
                .toList();
        if (agentNodes.size() != 1 || !Objects.equals(agentNodes.getFirst().agentId(), supervisorAgentId)) {
            issues.add(issue("SUPERVISOR_WORKFLOW_INVALID", "definition.workflow", "协同模式的主流程必须且只能包含所选Supervisor Agent"));
            return null;
        }
        return agentNodes.getFirst();
    }

    private static AgentCollaborationLimits resolveLimits(AgentCollaborationLimits limits, List<ApplicationValidationIssueVO> issues) {
        if (Objects.isNull(limits)) {
            issues.add(issue("COLLABORATION_LIMITS_REQUIRED", "definition.collaboration.limits", "协同模式缺少运行边界"));
            return null;
        }
        if (limits.maxDelegations() < MIN_DELEGATIONS || limits.maxDelegations() > MAX_DELEGATIONS) {
            issues.add(issue("MAX_DELEGATIONS_INVALID", "definition.collaboration.limits.maxDelegations", "最大委派次数必须在" + MIN_DELEGATIONS + "到" + MAX_DELEGATIONS + "之间"));
        }
        if (limits.maxDepth() != SUPERVISOR_MAX_DEPTH) {
            issues.add(issue("MAX_DEPTH_UNSUPPORTED", "definition.collaboration.limits.maxDepth", "当前阶段仅支持Supervisor到成员Agent的一层委派"));
        }
        return limits;
    }

    private ApplicationAgentReference resolveCollaborationAgent(ApplicationAgentReference reference, Long lockedVersionId, AgentType expectedType, String field, List<ApplicationValidationIssueVO> issues) {
        ApplicationAgentReferenceGateway.AgentReference agent = agentReferenceGateway.findAgent(reference.agentId()).orElse(null);
        if (Objects.isNull(agent)) {
            issues.add(issue("COLLABORATION_AGENT_NOT_FOUND", field, "协作Agent不存在"));
            return null;
        }
        if (!agent.active()) {
            issues.add(issue("COLLABORATION_AGENT_DISABLED", field, "协作Agent已停用"));
            return null;
        }
        if (agent.identity().agentType() != expectedType) {
            String message = expectedType == AgentType.SUPERVISOR
                    ? "Supervisor必须选择SUPERVISOR类型的Agent"
                    : "当前阶段的成员只能选择RAG_CHAT类型的Agent";
            issues.add(issue("COLLABORATION_AGENT_TYPE_INVALID", field, message));
            return null;
        }
        // 草稿只关联 Agent 主体。Supervisor 复用主 Workflow 已解析的版本，成员统一跟随当前生效版本。
        Long versionId = Objects.requireNonNullElse(lockedVersionId, agent.publishedVersionId());
        if (Objects.isNull(versionId)) {
            issues.add(issue("COLLABORATION_AGENT_UNPUBLISHED", field, "协作Agent尚无生效版本"));
            return null;
        }
        Long resolvedVersionId = agentReferenceGateway.findVersion(reference.agentId(), versionId).orElse(null);
        if (Objects.isNull(resolvedVersionId)) {
            issues.add(issue("COLLABORATION_AGENT_VERSION_NOT_FOUND", field, "协作Agent版本不存在或不属于所选Agent"));
            return null;
        }
        ApplicationAgentReferenceGateway.AgentIdentity identity = agent.identity();
        return new ApplicationAgentReference(identity.id(), resolvedVersionId, identity.name(), identity.description());
    }

    private AgentNodeConfig resolveAgentNode(
            String nodeId,
            AgentNodeConfig config,
            List<ApplicationValidationIssueVO> issues) {
        Long agentId = config.agentId();
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
        if (config.agentVersionId() == null) {
            versionId = agent.publishedVersionId();
            if (versionId == null) {
                issues.add(issue(
                        "AGENT_UNPUBLISHED",
                        nodeId,
                        "引用的Agent尚无生效版本"));
                return config;
            }
        } else {
            versionId = config.agentVersionId();
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
                agentId,
                resolvedVersionId,
                config.inputMapping(),
                config.outputMapping());
    }

    private static ApplicationValidationIssueVO issue(
            String code, String field, String message) {
        return new ApplicationValidationIssueVO(code, field, message);
    }
}

package com.lingxi.workbench.admin.application.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.admin.agent.common.enums.AgentType;
import com.lingxi.workbench.admin.agent.service.AgentReferenceService;
import com.lingxi.workbench.admin.application.model.vo.ApplicationValidationIssueVO;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelType;
import com.lingxi.workbench.admin.model.model.bo.ModelInstanceReferenceBO;
import com.lingxi.workbench.admin.model.service.ModelInstanceService;
import com.lingxi.workbench.admin.prompt.service.PromptService;
import com.lingxi.workbench.admin.tool.model.bo.ToolReferenceBO;
import com.lingxi.workbench.admin.tool.service.ToolService;
import com.lingxi.workbench.admin.workflow.service.WorkflowService;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import com.lingxi.workbench.workflow.exception.WorkflowDefinitionException;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.ApplicationContextPolicy;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import com.lingxi.workbench.workflow.model.AgentCollaborationDefinition;
import com.lingxi.workbench.workflow.model.AgentCollaborationLimits;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.workflow.model.ConversationSummaryMemoryConfig;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import com.lingxi.workbench.workflow.model.ContextualizationPolicy;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.CapabilityRiskLevel;
import com.lingxi.workbench.workflow.model.InteractionRecordMemoryConfig;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import com.lingxi.workbench.workflow.model.PlannerPolicy;
import com.lingxi.workbench.workflow.model.UserProfileMemoryConfig;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeType;
import com.lingxi.workbench.workflow.model.config.AgentNodeConfig;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import com.lingxi.workbench.workflow.model.intent.IntentFallbackPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentNodeDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentNodeType;
import com.lingxi.workbench.workflow.service.WorkflowDefinitionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

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
    private static final int MIN_CONTEXT_PARTITION_TOKENS = 128;
    private static final int MAX_CONTEXT_PARTITION_TOKENS = 200000;
    private static final int MIN_CONTEXTUALIZATION_OUTPUT_TOKENS = 16;
    private static final int MAX_CONTEXTUALIZATION_OUTPUT_TOKENS = 2048;
    private static final int MIN_CONTEXTUALIZATION_OUTPUT_CHARACTERS = 32;
    private static final int MAX_CONTEXTUALIZATION_OUTPUT_CHARACTERS = 8000;
    private static final int MAX_CONTEXTUALIZATION_TIMEOUT_SECONDS = 60;
    private static final int MIN_MEMORY_RETENTION_DAYS = 1;
    private static final int MAX_MEMORY_RETENTION_DAYS = 3650;
    private static final int MIN_SUMMARY_TRIGGER_TOKENS = 256;
    private static final int MAX_SUMMARY_TRIGGER_TOKENS = 16000;
    private static final Pattern CAPABILITY_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");
    private static final String DEFAULT_AGENT_CAPABILITY_CODE = "default_agent";

    private final AgentReferenceService agentReferenceService;
    private final ApplicationIntentTreeReferenceGateway intentTreeReferenceGateway;
    private final WorkflowService workflowService;
    private final ModelInstanceService modelInstanceService;
    private final PromptService promptService;
    private final ToolService toolService;
    private final WorkflowDefinitionValidator workflowValidator =
            new WorkflowDefinitionValidator();

    /** 解析草稿中 Agent 节点的版本引用并校验 Workflow 结构合法性，返回已锁定版本的定义。 */
    public ApplicationDefinitionResolution resolveForPublish(ApplicationDefinition draftDefinition) {
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

        WorkflowDefinition legacyWorkflow = resolveLegacyWorkflow(draftDefinition.workflow(), issues);
        AgentCollaborationDefinition collaboration = resolveCollaboration(draftDefinition.collaboration(), issues);
        ApplicationContextPolicy contextPolicy = resolveContextPolicy(draftDefinition.contextPolicy(), issues);
        ApplicationAgentReference defaultAgent = resolveDefaultAgent(draftDefinition.defaultAgent(), issues);
        CapabilityCatalogSnapshot capabilityCatalog = resolveCapabilityCatalog(
                draftDefinition.capabilityCatalog(), defaultAgent, issues);
        ApplicationIntentBinding fastRouteDraft = Objects.nonNull(draftDefinition.optionalFastRoutes())
                ? draftDefinition.optionalFastRoutes()
                : draftDefinition.intentBinding();
        FastRouteResolution fastRouteResolution = resolveFastRoutes(fastRouteDraft, capabilityCatalog, issues);
        MemoryConfig memory = resolveMemory(draftDefinition.memory(), fastRouteResolution.embeddingModelInstanceId(), issues);
        PlannerPolicy plannerPolicy = resolvePlannerPolicy(draftDefinition.plannerPolicy(), issues);
        ApplicationRuntimePolicy runtimePolicy = resolveRuntimePolicy(draftDefinition.runtimePolicy(), issues);
        validatePlannerRuntimeBudget(plannerPolicy, runtimePolicy, issues);
        if (Objects.isNull(defaultAgent)) {
            issues.add(issue("EXECUTION_ENTRY_REQUIRED", "definition.defaultAgent", "请选择默认ReAct Agent"));
        }
        if (!issues.isEmpty()) {
            return new ApplicationDefinitionResolution(null, issues);
        }
        ApplicationDefinition resolved = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .workflow(legacyWorkflow)
                .collaboration(collaboration)
                .optionalFastRoutes(fastRouteResolution.binding())
                .memory(memory)
                .contextPolicy(contextPolicy)
                .defaultAgent(defaultAgent)
                .plannerPolicy(plannerPolicy)
                .capabilityCatalog(capabilityCatalog)
                .runtimePolicy(runtimePolicy)
                .build();
        return new ApplicationDefinitionResolution(resolved, issues);
    }

    /** 默认入口只允许 ReAct Agent，并在发布时锁定不可变版本。 */
    private ApplicationAgentReference resolveDefaultAgent(ApplicationAgentReference draft, List<ApplicationValidationIssueVO> issues) {
        String field = "definition.defaultAgent";
        if (Objects.isNull(draft) || Objects.isNull(draft.agentId())) {
            return null;
        }
        AgentReferenceService.AgentReference agent = agentReferenceService.findAgent(draft.agentId()).orElse(null);
        if (Objects.isNull(agent) || !agent.active()) {
            issues.add(issue("DEFAULT_AGENT_UNAVAILABLE", field + ".agentId", "默认Agent不存在或已停用"));
            return null;
        }
        if (agent.identity().agentType() != AgentType.REACT) {
            issues.add(issue("DEFAULT_AGENT_TYPE_INVALID", field + ".agentId", "默认入口只能选择ReAct Agent"));
            return null;
        }
        Long versionId = Objects.requireNonNullElse(draft.agentVersionId(), agent.publishedVersionId());
        Long resolvedVersionId = Objects.isNull(versionId)
                ? null
                : agentReferenceService.findVersion(draft.agentId(), versionId).orElse(null);
        if (Objects.isNull(resolvedVersionId)) {
            issues.add(issue("DEFAULT_AGENT_VERSION_UNAVAILABLE", field + ".agentVersionId", "默认Agent没有可用正式版本"));
            return null;
        }
        AgentReferenceService.AgentIdentity identity = agent.identity();
        return new ApplicationAgentReference(identity.id(), resolvedVersionId, identity.name(), identity.description());
    }

    /** 解析能力目录，并自动将默认 Agent 作为保留能力写入不可变快照。 */
    private CapabilityCatalogSnapshot resolveCapabilityCatalog(CapabilityCatalogSnapshot draft, ApplicationAgentReference defaultAgent,
                                                                List<ApplicationValidationIssueVO> issues) {
        List<ApplicationCapabilityDefinition> resolved = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        if (Objects.nonNull(defaultAgent)) {
            codes.add(DEFAULT_AGENT_CAPABILITY_CODE);
            resolved.add(agentCapability(DEFAULT_AGENT_CAPABILITY_CODE, defaultAgent.name(), defaultAgent.description(),
                    defaultAgent.agentId(), defaultAgent.agentVersionId()));
        }
        List<ApplicationCapabilityDefinition> capabilities = Objects.isNull(draft) ? List.of() : draft.capabilities();
        if (capabilities.size() > 50) {
            issues.add(issue("CAPABILITY_COUNT_INVALID", "definition.capabilityCatalog.capabilities", "能力目录最多配置50项能力"));
            return CapabilityCatalogSnapshot.builder().capabilities(resolved).build();
        }
        for (int index = 0; index < capabilities.size(); index++) {
            ApplicationCapabilityDefinition capability = capabilities.get(index);
            String field = "definition.capabilityCatalog.capabilities[" + index + "]";
            ApplicationCapabilityDefinition item = resolveCapability(capability, field, codes, issues);
            if (Objects.nonNull(item)) {
                resolved.add(item);
            }
        }
        return CapabilityCatalogSnapshot.builder().capabilities(List.copyOf(resolved)).build();
    }

    private ApplicationCapabilityDefinition resolveCapability(ApplicationCapabilityDefinition draft, String field, Set<String> codes,
                                                              List<ApplicationValidationIssueVO> issues) {
        if (Objects.isNull(draft) || isBlank(draft.getCode()) || !CAPABILITY_CODE_PATTERN.matcher(draft.getCode()).matches()) {
            issues.add(issue("CAPABILITY_CODE_INVALID", field + ".code", "能力编码需使用2到64位小写字母、数字或下划线"));
            return null;
        }
        if (!codes.add(draft.getCode())) {
            issues.add(issue("CAPABILITY_CODE_DUPLICATED", field + ".code", "能力编码不能重复或使用保留编码default_agent"));
            return null;
        }
        if (Objects.isNull(draft.getType()) || Objects.isNull(draft.getTargetId())) {
            issues.add(issue("CAPABILITY_TARGET_REQUIRED", field, "请选择能力类型和目标资源"));
            return null;
        }
        validateOptionalRange(draft.getTimeoutSeconds(), 1, 600, "CAPABILITY_TIMEOUT_INVALID",
                field + ".timeoutSeconds", "能力超时时间", issues);
        validateOptionalRange(draft.getMaxCalls(), 1, 20, "CAPABILITY_CALLS_INVALID",
                field + ".maxCalls", "能力调用次数", issues);
        validateOptionalRange(draft.getResultTokenBudget(), 128, 32000, "CAPABILITY_RESULT_BUDGET_INVALID",
                field + ".resultTokenBudget", "能力结果Token预算", issues);
        return switch (draft.getType()) {
            case REACT_AGENT -> resolveAgentCapability(draft, field, issues);
            case WORKFLOW -> resolveWorkflowCapability(draft, field, issues);
            case TOOL -> resolveToolCapability(draft, field, issues);
        };
    }

    private ApplicationCapabilityDefinition resolveAgentCapability(ApplicationCapabilityDefinition draft, String field,
                                                                   List<ApplicationValidationIssueVO> issues) {
        AgentReferenceService.AgentReference agent = agentReferenceService.findAgent(draft.getTargetId()).orElse(null);
        if (Objects.isNull(agent) || !agent.active() || agent.identity().agentType() != AgentType.REACT) {
            issues.add(issue("CAPABILITY_AGENT_UNAVAILABLE", field + ".targetId", "能力目标必须是已启用的ReAct Agent"));
            return null;
        }
        Long versionId = Objects.requireNonNullElse(draft.getTargetVersionId(), agent.publishedVersionId());
        if (Objects.isNull(versionId) || agentReferenceService.findVersion(draft.getTargetId(), versionId).isEmpty()) {
            issues.add(issue("CAPABILITY_AGENT_VERSION_UNAVAILABLE", field + ".targetVersionId", "能力目标Agent没有可用正式版本"));
            return null;
        }
        return agentCapability(draft.getCode(), agent.identity().name(), normalizedDescription(draft, agent.identity().description()),
                draft.getTargetId(), versionId);
    }

    private ApplicationCapabilityDefinition resolveWorkflowCapability(ApplicationCapabilityDefinition draft, String field,
                                                                      List<ApplicationValidationIssueVO> issues) {
        WorkflowService.WorkflowReference workflow = workflowService.findReference(draft.getTargetId()).orElse(null);
        if (Objects.isNull(workflow) || !workflow.active()) {
            issues.add(issue("CAPABILITY_WORKFLOW_UNAVAILABLE", field + ".targetId", "目标Workflow不存在或已停用"));
            return null;
        }
        Long versionId = Objects.requireNonNullElse(draft.getTargetVersionId(), workflow.publishedVersionId());
        WorkflowDefinition definition = Objects.isNull(versionId)
                ? null
                : workflowService.findVersion(draft.getTargetId(), versionId).orElse(null);
        if (Objects.isNull(definition)) {
            issues.add(issue("CAPABILITY_WORKFLOW_VERSION_UNAVAILABLE", field + ".targetVersionId", "目标Workflow没有可用正式版本"));
            return null;
        }
        return baseCapability(draft, workflow.name(), draft.getDescription(), versionId, queryInputSchema(), responseOutputSchema(),
                "WORKFLOW_VERSION", checksum(definition));
    }

    private ApplicationCapabilityDefinition resolveToolCapability(ApplicationCapabilityDefinition draft, String field,
                                                                  List<ApplicationValidationIssueVO> issues) {
        ToolReferenceBO tool = toolService.findReference(draft.getTargetId()).orElse(null);
        if (Objects.isNull(tool) || tool.getStatus() != EnableStatus.ACTIVE) {
            issues.add(issue("CAPABILITY_TOOL_UNAVAILABLE", field + ".targetId", "目标Tool不存在或已停用"));
            return null;
        }
        JSONObject configuration = new JSONObject();
        configuration.put("toolCode", tool.getCode());
        configuration.put("sourceType", tool.getSourceType());
        configuration.put("mcpServerId", tool.getMcpServerId());
        configuration.put("remoteToolName", tool.getRemoteToolName());
        configuration.put("inputSchema", tool.getParameterSchema());
        return baseCapability(draft, tool.getName(), normalizedDescription(draft, tool.getDescription()), null,
                tool.getParameterSchema(), genericObjectSchema(), tool.getSourceType().name(), checksum(configuration));
    }

    private static ApplicationCapabilityDefinition agentCapability(String code, String name, String description, Long targetId, Long versionId) {
        ApplicationCapabilityDefinition draft = ApplicationCapabilityDefinition.builder()
                .code(code)
                .type(ApplicationCapabilityType.REACT_AGENT)
                .targetId(targetId)
                .description(description)
                .riskLevel(CapabilityRiskLevel.READ_ONLY)
                .timeoutSeconds(120)
                .maxCalls(1)
                .resultTokenBudget(8192)
                .build();
        return baseCapability(draft, name, description, versionId, queryInputSchema(), responseOutputSchema(),
                "REACT_AGENT_VERSION", checksum(versionId));
    }

    private static ApplicationCapabilityDefinition baseCapability(ApplicationCapabilityDefinition draft, String name, String description,
                                                                  Long versionId, JSONObject inputSchema, JSONObject outputSchema,
                                                                  String implementationType, String configurationChecksum) {
        return ApplicationCapabilityDefinition.builder()
                .code(draft.getCode())
                .type(draft.getType())
                .name(name)
                .description(isBlank(description) ? name : description.trim())
                .targetId(draft.getTargetId())
                .targetVersionId(versionId)
                .inputSchema(inputSchema)
                .outputSchema(outputSchema)
                .implementationType(implementationType)
                .configurationChecksum(configurationChecksum)
                .riskLevel(Objects.requireNonNullElse(draft.getRiskLevel(), CapabilityRiskLevel.READ_ONLY))
                .timeoutSeconds(defaultIfNull(draft.getTimeoutSeconds(), 120))
                .maxCalls(defaultIfNull(draft.getMaxCalls(), 1))
                .resultTokenBudget(defaultIfNull(draft.getResultTokenBudget(), 8192))
                .build();
    }

    private static String normalizedDescription(ApplicationCapabilityDefinition draft, String fallback) {
        return isBlank(draft.getDescription()) ? fallback : draft.getDescription();
    }

    private PlannerPolicy resolvePlannerPolicy(PlannerPolicy draft, List<ApplicationValidationIssueVO> issues) {
        PlannerPolicy policy = Objects.requireNonNullElseGet(draft, PlannerPolicy::disabled);
        if (!policy.isEnabled()) {
            return PlannerPolicy.disabled();
        }
        validatePlannerModel(policy.getChatModelInstanceId(), issues);
        validatePlannerPrompt(policy.getPromptVersionId(), issues);
        validateRange(policy.getMaxSteps(), 1, 20, "PLANNER_STEPS_INVALID", "definition.plannerPolicy.maxSteps", "计划步骤数", issues);
        validateRange(policy.getMaxReplans(), 0, 3, "PLANNER_REPLANS_INVALID", "definition.plannerPolicy.maxReplans", "重规划次数", issues);
        validateRange(policy.getMaxModelCalls(), 2, 100, "PLANNER_MODEL_CALLS_INVALID", "definition.plannerPolicy.maxModelCalls", "规划模型调用次数", issues);
        validateRange(policy.getMaxToolCalls(), 0, 100, "PLANNER_TOOL_CALLS_INVALID", "definition.plannerPolicy.maxToolCalls", "计划Tool调用次数", issues);
        validateRange(policy.getTokenBudget(), 1024, 200000, "PLANNER_TOKEN_BUDGET_INVALID", "definition.plannerPolicy.tokenBudget", "规划Token预算", issues);
        validateRange(policy.getTimeoutSeconds(), 5, 3600, "PLANNER_TIMEOUT_INVALID", "definition.plannerPolicy.timeoutSeconds", "规划超时时间", issues);
        return PlannerPolicy.builder()
                .enabled(true)
                .chatModelInstanceId(policy.getChatModelInstanceId())
                .promptVersionId(policy.getPromptVersionId())
                .maxSteps(policy.getMaxSteps())
                .maxReplans(policy.getMaxReplans())
                .maxModelCalls(policy.getMaxModelCalls())
                .maxToolCalls(policy.getMaxToolCalls())
                .tokenBudget(policy.getTokenBudget())
                .timeoutSeconds(policy.getTimeoutSeconds())
                .build();
    }

    /** Planner 只能使用 Application 总运行预算的子集。 */
    private static void validatePlannerRuntimeBudget(PlannerPolicy planner, ApplicationRuntimePolicy runtime,
                                                     List<ApplicationValidationIssueVO> issues) {
        if (!planner.isEnabled()) {
            return;
        }
        if (planner.getMaxModelCalls() > runtime.getMaxModelCalls()) {
            issues.add(issue("PLANNER_MODEL_BUDGET_EXCEEDED", "definition.plannerPolicy.maxModelCalls", "Planner模型调用上限不能超过Application总上限"));
        }
        if (planner.getMaxToolCalls() > runtime.getMaxToolCalls()) {
            issues.add(issue("PLANNER_TOOL_BUDGET_EXCEEDED", "definition.plannerPolicy.maxToolCalls", "Planner Tool调用上限不能超过Application总上限"));
        }
        if (planner.getTokenBudget() > runtime.getTotalTokenBudget()) {
            issues.add(issue("PLANNER_TOKEN_BUDGET_EXCEEDED", "definition.plannerPolicy.tokenBudget", "Planner Token预算不能超过Application总预算"));
        }
        if (planner.getTimeoutSeconds() > runtime.getTimeoutSeconds()) {
            issues.add(issue("PLANNER_TIMEOUT_EXCEEDED", "definition.plannerPolicy.timeoutSeconds", "Planner超时时间不能超过Application总超时"));
        }
    }

    private static ApplicationRuntimePolicy resolveRuntimePolicy(ApplicationRuntimePolicy draft, List<ApplicationValidationIssueVO> issues) {
        ApplicationRuntimePolicy policy = Objects.requireNonNullElseGet(draft, ApplicationRuntimePolicy::defaults);
        validateRange(policy.getMaxModelCalls(), 1, 100, "RUNTIME_MODEL_CALLS_INVALID", "definition.runtimePolicy.maxModelCalls", "模型调用次数", issues);
        validateRange(policy.getMaxToolCalls(), 0, 100, "RUNTIME_TOOL_CALLS_INVALID", "definition.runtimePolicy.maxToolCalls", "Tool调用次数", issues);
        validateRange(policy.getMaxDelegations(), 0, 10, "RUNTIME_DELEGATIONS_INVALID", "definition.runtimePolicy.maxDelegations", "委派次数", issues);
        validateRange(policy.getTotalTokenBudget(), 1024, 1000000, "RUNTIME_TOKEN_BUDGET_INVALID", "definition.runtimePolicy.totalTokenBudget", "总Token预算", issues);
        validateRange(policy.getTimeoutSeconds(), 1, 3600, "RUNTIME_TIMEOUT_INVALID", "definition.runtimePolicy.timeoutSeconds", "总超时时间", issues);
        return policy;
    }

    private static int defaultIfNull(Integer value, int defaultValue) {
        if (Objects.isNull(value)) {
            return defaultValue;
        }
        return value;
    }

    private static void validateOptionalRange(Integer value, int minimum, int maximum, String code, String field,
                                              String displayName, List<ApplicationValidationIssueVO> issues) {
        if (Objects.nonNull(value)) {
            validateRange(value, minimum, maximum, code, field, displayName, issues);
        }
    }

    private static JSONObject queryInputSchema() {
        JSONObject query = JSONObject.of("type", "string", "description", "用户当前请求");
        return JSONObject.of("type", "object", "properties", JSONObject.of("query", query),
                "required", List.of("query"), "additionalProperties", false);
    }

    private static JSONObject responseOutputSchema() {
        JSONObject response = JSONObject.of("type", "string", "description", "能力执行结果");
        return JSONObject.of("type", "object", "properties", JSONObject.of("response", response),
                "required", List.of("response"), "additionalProperties", false);
    }

    private static JSONObject genericObjectSchema() {
        return JSONObject.of("type", "object", "additionalProperties", true);
    }

    private static String checksum(Object value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(JSON.toJSONBytes(value)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少SHA-256算法", exception);
        }
    }

    /** 仅为迁移前的历史草稿保留内嵌 Workflow 解析，新草稿不会进入该分支。 */
    private WorkflowDefinition resolveLegacyWorkflow(WorkflowDefinition workflow, List<ApplicationValidationIssueVO> issues) {
        if (Objects.isNull(workflow)) {
            return null;
        }
        try {
            workflowValidator.validateDraft(workflow);
        } catch (WorkflowDefinitionException exception) {
            issues.add(issue("WORKFLOW_INVALID", "definition.workflow", exception.getMessage()));
            return null;
        }
        int issueCountBeforeReferenceResolution = issues.size();
        List<WorkflowNodeDefinition> resolvedNodes = new ArrayList<>(workflow.nodes().size());
        for (WorkflowNodeDefinition node : workflow.nodes()) {
            if (node.config() instanceof AgentNodeConfig config) {
                resolvedNodes.add(new WorkflowNodeDefinition(
                        node.id(), node.name(), node.type(), resolveAgentNode(node.id(), config, issues)));
            } else {
                resolvedNodes.add(node);
            }
        }
        WorkflowDefinition resolved = new WorkflowDefinition(
                workflow.schemaVersion(), workflow.code(), resolvedNodes, workflow.edges());
        if (issues.size() > issueCountBeforeReferenceResolution) {
            return resolved;
        }
        try {
            workflowValidator.validate(resolved);
        } catch (WorkflowDefinitionException exception) {
            issues.add(issue("WORKFLOW_INVALID", "definition.workflow", exception.getMessage()));
        }
        return resolved;
    }

    /** 校验并锁定可选快速路由；只要求被选中的叶子映射到已发布能力。 */
    private FastRouteResolution resolveFastRoutes(ApplicationIntentBinding draft,
                                                  CapabilityCatalogSnapshot capabilityCatalog,
                                                  List<ApplicationValidationIssueVO> issues) {
        if (Objects.isNull(draft) || !draft.isEnabled()) {
            return new FastRouteResolution(null, null);
        }
        int initialIssueCount = issues.size();
        if (Objects.isNull(draft.getIntentTreeId())) {
            issues.add(issue("FAST_ROUTE_TREE_REQUIRED", "definition.optionalFastRoutes.intentTreeId", "请选择意图树"));
            return new FastRouteResolution(null, null);
        }
        ApplicationIntentTreeReferenceGateway.IntentTreeReference tree = intentTreeReferenceGateway
                .findPublishedTree(draft.getIntentTreeId())
                .orElse(null);
        if (Objects.isNull(tree)) {
            issues.add(issue("FAST_ROUTE_TREE_NOT_FOUND", "definition.optionalFastRoutes.intentTreeId", "意图树不存在"));
            return new FastRouteResolution(null, null);
        }
        if (!tree.active()) {
            issues.add(issue("FAST_ROUTE_TREE_DISABLED", "definition.optionalFastRoutes.intentTreeId", "意图树已停用"));
        }
        if (Objects.isNull(tree.versionId()) || Objects.isNull(tree.definition())) {
            issues.add(issue("FAST_ROUTE_TREE_UNPUBLISHED", "definition.optionalFastRoutes.intentTreeId", "意图树尚无可用正式版本"));
            return new FastRouteResolution(null, null);
        }

        Set<String> leafCodes = tree.definition().nodes().stream()
                .filter(IntentNodeDefinition::isEnabled)
                .filter(node -> node.getNodeType() == IntentNodeType.LEAF)
                .map(IntentNodeDefinition::getCode)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        List<ApplicationIntentMapping> resolvedMappings = new ArrayList<>();
        Set<String> mappedCodes = new HashSet<>();
        List<ApplicationIntentMapping> mappings = draft.mappings();
        if (mappings.isEmpty()) {
            issues.add(issue("FAST_ROUTE_MAPPING_REQUIRED", "definition.optionalFastRoutes.mappings", "请至少配置一个需要确定性直达的叶子"));
        }
        for (int index = 0; index < mappings.size(); index++) {
            ApplicationIntentMapping mapping = mappings.get(index);
            String field = "definition.optionalFastRoutes.mappings[" + index + "]";
            if (Objects.isNull(mapping) || !leafCodes.contains(mapping.getIntentCode())) {
                issues.add(issue("FAST_ROUTE_MAPPING_UNKNOWN", field + ".intentCode", "快速路由必须引用当前正式版本中的启用叶子意图"));
                continue;
            }
            if (!mappedCodes.add(mapping.getIntentCode())) {
                issues.add(issue("FAST_ROUTE_MAPPING_DUPLICATED", field + ".intentCode", "同一叶子意图不能重复配置快速路由"));
                continue;
            }
            ApplicationIntentMapping resolved = resolveFastRouteTarget(mapping, capabilityCatalog, field, issues);
            if (Objects.nonNull(resolved)) {
                resolvedMappings.add(resolved);
            }
        }
        if (issues.size() > initialIssueCount) {
            return new FastRouteResolution(null, tree.definition().embeddingModelInstanceId());
        }
        ApplicationIntentBinding binding = ApplicationIntentBinding.builder()
                .enabled(true)
                .intentTreeId(tree.intentTreeId())
                .intentTreeVersionId(tree.versionId())
                .mappings(resolvedMappings)
                .build();
        return new FastRouteResolution(binding, tree.definition().embeddingModelInstanceId());
    }

    private ApplicationIntentMapping resolveFastRouteTarget(ApplicationIntentMapping mapping,
                                                             CapabilityCatalogSnapshot capabilityCatalog,
                                                             String field,
                                                             List<ApplicationValidationIssueVO> issues) {
        if (Objects.isNull(mapping.getTargetType())) {
            issues.add(issue("FAST_ROUTE_TARGET_REQUIRED", field + ".targetType", "请选择快速路由执行目标"));
            return null;
        }
        if (mapping.getTargetType() == IntentExecutionTargetType.FIXED_RESPONSE) {
            if (isBlank(mapping.getFixedResponse())) {
                issues.add(issue("FIXED_RESPONSE_REQUIRED", field + ".fixedResponse", "固定答复不能为空"));
                return null;
            }
            return ApplicationIntentMapping.builder()
                    .intentCode(mapping.getIntentCode())
                    .targetType(IntentExecutionTargetType.FIXED_RESPONSE)
                    .fixedResponse(mapping.getFixedResponse().trim())
                    .build();
        }
        ApplicationCapabilityType expectedType = mapping.getTargetType() == IntentExecutionTargetType.WORKFLOW
                ? ApplicationCapabilityType.WORKFLOW
                : ApplicationCapabilityType.REACT_AGENT;
        ApplicationCapabilityDefinition capability = findFastRouteCapability(mapping, expectedType, capabilityCatalog);
        if (Objects.isNull(capability)) {
            issues.add(issue("FAST_ROUTE_CAPABILITY_UNAVAILABLE", field + ".capabilityCode", "请选择能力目录中唯一可用的执行能力"));
            return null;
        }
        return ApplicationIntentMapping.builder()
                .intentCode(mapping.getIntentCode())
                .targetType(mapping.getTargetType())
                .capabilityCode(capability.getCode())
                .targetId(capability.getTargetId())
                .targetVersionId(capability.getTargetVersionId())
                .build();
    }

    private static ApplicationCapabilityDefinition findFastRouteCapability(ApplicationIntentMapping mapping, ApplicationCapabilityType expectedType,
                                                                            CapabilityCatalogSnapshot catalog) {
        List<ApplicationCapabilityDefinition> matches = catalog.capabilities().stream()
                .filter(capability -> capability.getType() == expectedType)
                .filter(capability -> isBlank(mapping.getCapabilityCode())
                        ? Objects.equals(capability.getTargetId(), mapping.getTargetId())
                        : Objects.equals(capability.getCode(), mapping.getCapabilityCode()))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    /** 分别校验三类记忆配置，交互记录在启用快速路由时复用意图树向量模型。 */
    private MemoryConfig resolveMemory(MemoryConfig draft, Long intentEmbeddingModelInstanceId, List<ApplicationValidationIssueVO> issues) {
        MemoryConfig source = Objects.requireNonNullElseGet(draft, MemoryConfig::disabled);
        return MemoryConfig.builder()
                .profile(resolveProfileMemory(source.profile(), issues))
                .interactionRecord(resolveInteractionMemory(source.interactionRecord(), intentEmbeddingModelInstanceId, issues))
                .summary(resolveSummaryMemory(source.summary(), issues))
                .build();
    }

    private UserProfileMemoryConfig resolveProfileMemory(UserProfileMemoryConfig draft, List<ApplicationValidationIssueVO> issues) {
        if (!draft.isEnabled()) {
            return UserProfileMemoryConfig.disabled();
        }
        validateMemoryModel(draft.getChatModelInstanceId(), ModelType.CHAT, "definition.memory.profile.chatModelInstanceId", issues);
        validateMemoryPrompt(draft.getPromptVersionId(), PromptRole.SYSTEM, "definition.memory.profile.promptVersionId", issues);
        return UserProfileMemoryConfig.builder()
                .enabled(true)
                .chatModelInstanceId(draft.getChatModelInstanceId())
                .promptVersionId(draft.getPromptVersionId())
                .updateStrategy(draft.effectiveUpdateStrategy())
                .build();
    }

    private InteractionRecordMemoryConfig resolveInteractionMemory(InteractionRecordMemoryConfig draft, Long intentEmbeddingModelInstanceId,
                                                                    List<ApplicationValidationIssueVO> issues) {
        if (!draft.isEnabled()) {
            return InteractionRecordMemoryConfig.disabled();
        }
        Long embeddingModelInstanceId = draft.getEmbeddingModelInstanceId();
        if (Objects.nonNull(intentEmbeddingModelInstanceId)) {
            if (Objects.nonNull(embeddingModelInstanceId)
                    && !Objects.equals(embeddingModelInstanceId, intentEmbeddingModelInstanceId)) {
                issues.add(issue("MEMORY_EMBEDDING_INTENT_MISMATCH",
                        "definition.memory.interactionRecord.embeddingModelInstanceId",
                        "交互记录Embedding模型必须与意图树正式版本一致"));
            }
            embeddingModelInstanceId = intentEmbeddingModelInstanceId;
        }
        validateMemoryModel(embeddingModelInstanceId, ModelType.EMBEDDING,
                "definition.memory.interactionRecord.embeddingModelInstanceId", issues);
        validateMemoryModel(draft.getChatModelInstanceId(), ModelType.CHAT,
                "definition.memory.interactionRecord.chatModelInstanceId", issues);
        validateMemoryPrompt(draft.getPromptVersionId(), PromptRole.SYSTEM,
                "definition.memory.interactionRecord.promptVersionId", issues);
        if (draft.effectiveTopK() < 1 || draft.effectiveTopK() > 10) {
            issues.add(issue("MEMORY_TOP_K_INVALID", "definition.memory.interactionRecord.topK",
                    "交互记录检索条数必须在1到10之间"));
        }
        validateRange(draft.effectiveRetentionDays(), MIN_MEMORY_RETENTION_DAYS, MAX_MEMORY_RETENTION_DAYS,
                "MEMORY_RETENTION_DAYS_INVALID", "definition.memory.interactionRecord.retentionDays", "交互记录保留天数", issues);
        return InteractionRecordMemoryConfig.builder()
                .enabled(true)
                .embeddingModelInstanceId(embeddingModelInstanceId)
                .chatModelInstanceId(draft.getChatModelInstanceId())
                .promptVersionId(draft.getPromptVersionId())
                .topK(draft.effectiveTopK())
                .retentionDays(draft.effectiveRetentionDays())
                .build();
    }

    private ConversationSummaryMemoryConfig resolveSummaryMemory(ConversationSummaryMemoryConfig draft, List<ApplicationValidationIssueVO> issues) {
        if (!draft.isEnabled()) {
            return ConversationSummaryMemoryConfig.disabled();
        }
        validateMemoryModel(draft.getChatModelInstanceId(), ModelType.CHAT,
                "definition.memory.summary.chatModelInstanceId", issues);
        validateMemoryPrompt(draft.getPromptVersionId(), PromptRole.SYSTEM,
                "definition.memory.summary.promptVersionId", issues);
        if (draft.effectiveMinRounds() < 4 || draft.effectiveMinRounds() > 50) {
            issues.add(issue("MEMORY_SUMMARY_ROUNDS_INVALID", "definition.memory.summary.minRounds",
                    "摘要阈值必须在4到50轮之间"));
        }
        validateRange(draft.effectiveMinTokens(), MIN_SUMMARY_TRIGGER_TOKENS, MAX_SUMMARY_TRIGGER_TOKENS,
                "MEMORY_SUMMARY_TOKENS_INVALID", "definition.memory.summary.minTokens", "摘要Token阈值", issues);
        return ConversationSummaryMemoryConfig.builder()
                .enabled(true)
                .chatModelInstanceId(draft.getChatModelInstanceId())
                .promptVersionId(draft.getPromptVersionId())
                .minRounds(draft.effectiveMinRounds())
                .minTokens(draft.effectiveMinTokens())
                .build();
    }

    /** 校验上下文化引用和各上下文分区预算，生成可直接冻结的规范配置。 */
    private ApplicationContextPolicy resolveContextPolicy(ApplicationContextPolicy draft, List<ApplicationValidationIssueVO> issues) {
        ApplicationContextPolicy source = Objects.requireNonNullElseGet(draft, ApplicationContextPolicy::defaults);
        ContextualizationPolicy contextualization = resolveContextualization(source.contextualization(), issues);
        ContextBudgetPolicy budget = resolveContextBudget(source.budget(), issues);
        return ApplicationContextPolicy.builder().contextualization(contextualization).budget(budget).build();
    }

    private ContextualizationPolicy resolveContextualization(ContextualizationPolicy draft, List<ApplicationValidationIssueVO> issues) {
        if (!draft.isEnabled()) {
            return ContextualizationPolicy.disabled();
        }
        validateContextualizationModel(draft.getChatModelInstanceId(), issues);
        validateContextualizationPrompt(draft.getPromptVersionId(), issues);
        validateRange(draft.effectiveMaxOutputTokens(), MIN_CONTEXTUALIZATION_OUTPUT_TOKENS, MAX_CONTEXTUALIZATION_OUTPUT_TOKENS,
                "CONTEXTUALIZATION_OUTPUT_TOKENS_INVALID", "definition.contextPolicy.contextualization.maxOutputTokens", "上下文化输出Token上限", issues);
        validateRange(draft.effectiveMaxOutputCharacters(), MIN_CONTEXTUALIZATION_OUTPUT_CHARACTERS, MAX_CONTEXTUALIZATION_OUTPUT_CHARACTERS,
                "CONTEXTUALIZATION_OUTPUT_CHARACTERS_INVALID", "definition.contextPolicy.contextualization.maxOutputCharacters", "上下文化输出字符上限", issues);
        validateRange(draft.effectiveTimeoutSeconds(), 1, MAX_CONTEXTUALIZATION_TIMEOUT_SECONDS,
                "CONTEXTUALIZATION_TIMEOUT_INVALID", "definition.contextPolicy.contextualization.timeoutSeconds", "上下文化超时时间", issues);
        return ContextualizationPolicy.builder()
                .enabled(true)
                .chatModelInstanceId(draft.getChatModelInstanceId())
                .promptVersionId(draft.getPromptVersionId())
                .fallbackToOriginal(draft.isFallbackToOriginal())
                .maxOutputTokens(draft.effectiveMaxOutputTokens())
                .maxOutputCharacters(draft.effectiveMaxOutputCharacters())
                .timeoutSeconds(draft.effectiveTimeoutSeconds())
                .build();
    }

    private static ContextBudgetPolicy resolveContextBudget(ContextBudgetPolicy draft, List<ApplicationValidationIssueVO> issues) {
        validateRange(draft.getMaxHistoryRounds(), 1, 100, "CONTEXT_HISTORY_ROUNDS_INVALID",
                "definition.contextPolicy.budget.maxHistoryRounds", "历史轮数上限", issues);
        validateContextPartition(draft.getMaxSystemPromptTokens(), "maxSystemPromptTokens", "系统提示词", issues);
        validateContextPartition(draft.getMaxCapabilityTokens(), "maxCapabilityTokens", "能力描述", issues);
        validateContextPartition(draft.getMaxHistoryTokens(), "maxHistoryTokens", "历史消息", issues);
        validateContextPartition(draft.getMaxMemoryTokens(), "maxMemoryTokens", "长期记忆", issues);
        validateContextPartition(draft.getMaxInputTokens(), "maxInputTokens", "当前输入", issues);
        validateContextPartition(draft.getMaxObservationTokens(), "maxObservationTokens", "Observation", issues);
        validateContextPartition(draft.getReservedOutputTokens(), "reservedOutputTokens", "回答输出预留", issues);
        return ContextBudgetPolicy.builder()
                .maxHistoryRounds(draft.getMaxHistoryRounds())
                .maxSystemPromptTokens(draft.getMaxSystemPromptTokens())
                .maxCapabilityTokens(draft.getMaxCapabilityTokens())
                .maxHistoryTokens(draft.getMaxHistoryTokens())
                .maxMemoryTokens(draft.getMaxMemoryTokens())
                .maxInputTokens(draft.getMaxInputTokens())
                .maxObservationTokens(draft.getMaxObservationTokens())
                .reservedOutputTokens(draft.getReservedOutputTokens())
                .build();
    }

    private static void validateContextPartition(int value, String fieldName, String displayName, List<ApplicationValidationIssueVO> issues) {
        validateRange(value, MIN_CONTEXT_PARTITION_TOKENS, MAX_CONTEXT_PARTITION_TOKENS, "CONTEXT_BUDGET_INVALID",
                "definition.contextPolicy.budget." + fieldName, displayName + "Token预算", issues);
    }

    private static void validateRange(int value, int minimum, int maximum, String code, String field, String displayName, List<ApplicationValidationIssueVO> issues) {
        if (value < minimum || value > maximum) {
            issues.add(issue(code, field, displayName + "必须在" + minimum + "到" + maximum + "之间"));
        }
    }

    private void validateMemoryModel(Long modelId, ModelType expectedType, String field, List<ApplicationValidationIssueVO> issues) {
        if (Objects.isNull(modelId)) {
            issues.add(issue("MEMORY_MODEL_REQUIRED", field, "请选择记忆系统使用的" + expectedType.getDescribe()));
            return;
        }
        ModelInstanceReferenceBO model = modelInstanceService.findReference(modelId).orElse(null);
        if (Objects.isNull(model) || model.status() != EnableStatus.ACTIVE || model.modelType() != expectedType) {
            issues.add(issue("MEMORY_MODEL_UNAVAILABLE", field, "记忆系统模型不存在、已停用或类型不匹配"));
        }
    }

    private void validateContextualizationModel(Long modelId, List<ApplicationValidationIssueVO> issues) {
        String field = "definition.contextPolicy.contextualization.chatModelInstanceId";
        if (Objects.isNull(modelId)) {
            issues.add(issue("CONTEXTUALIZATION_MODEL_REQUIRED", field, "请选择上下文化使用的Chat模型"));
            return;
        }
        ModelInstanceReferenceBO model = modelInstanceService.findReference(modelId).orElse(null);
        if (Objects.isNull(model) || model.status() != EnableStatus.ACTIVE || model.modelType() != ModelType.CHAT) {
            issues.add(issue("CONTEXTUALIZATION_MODEL_UNAVAILABLE", field, "上下文化Chat模型不存在、已停用或类型不匹配"));
        }
    }

    private void validateContextualizationPrompt(Long versionId, List<ApplicationValidationIssueVO> issues) {
        String field = "definition.contextPolicy.contextualization.promptVersionId";
        if (Objects.isNull(versionId)) {
            issues.add(issue("CONTEXTUALIZATION_PROMPT_REQUIRED", field, "请选择已发布的上下文化SYSTEM Prompt版本"));
            return;
        }
        PromptService.PromptVersionReference prompt = promptService.findPublishedVersion(versionId).orElse(null);
        if (Objects.isNull(prompt) || !prompt.active() || prompt.role() != PromptRole.SYSTEM) {
            issues.add(issue("CONTEXTUALIZATION_PROMPT_UNAVAILABLE", field, "上下文化Prompt版本不存在、已停用或角色不匹配"));
        }
    }

    private void validatePlannerModel(Long modelId, List<ApplicationValidationIssueVO> issues) {
        String field = "definition.plannerPolicy.chatModelInstanceId";
        if (Objects.isNull(modelId)) {
            issues.add(issue("PLANNER_MODEL_REQUIRED", field, "请选择Planner使用的Chat模型"));
            return;
        }
        ModelInstanceReferenceBO model = modelInstanceService.findReference(modelId).orElse(null);
        if (Objects.isNull(model) || model.status() != EnableStatus.ACTIVE || model.modelType() != ModelType.CHAT) {
            issues.add(issue("PLANNER_MODEL_UNAVAILABLE", field, "Planner Chat模型不存在、已停用或类型不匹配"));
        }
    }

    private void validatePlannerPrompt(Long versionId, List<ApplicationValidationIssueVO> issues) {
        String field = "definition.plannerPolicy.promptVersionId";
        if (Objects.isNull(versionId)) {
            issues.add(issue("PLANNER_PROMPT_REQUIRED", field, "请选择已发布的Planner SYSTEM Prompt版本"));
            return;
        }
        PromptService.PromptVersionReference prompt = promptService.findPublishedVersion(versionId).orElse(null);
        if (Objects.isNull(prompt) || !prompt.active() || prompt.role() != PromptRole.SYSTEM) {
            issues.add(issue("PLANNER_PROMPT_UNAVAILABLE", field, "Planner Prompt版本不存在、已停用或角色不匹配"));
        }
    }

    private void validateMemoryPrompt(Long versionId, PromptRole expectedRole, String field, List<ApplicationValidationIssueVO> issues) {
        if (Objects.isNull(versionId)) {
            issues.add(issue("MEMORY_PROMPT_REQUIRED", field, "请选择已发布的记忆Prompt版本"));
            return;
        }
        PromptService.PromptVersionReference prompt = promptService.findPublishedVersion(versionId).orElse(null);
        if (Objects.isNull(prompt) || !prompt.active() || prompt.role() != expectedRole) {
            issues.add(issue("MEMORY_PROMPT_UNAVAILABLE", field, "记忆Prompt版本不存在、已停用或角色不匹配"));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 将草稿中的稳定 Agent 主键解析成不可变版本引用，并验证 V1 Supervisor 拓扑。
     */
    private AgentCollaborationDefinition resolveCollaboration(AgentCollaborationDefinition draft, List<ApplicationValidationIssueVO> issues) {
        if (Objects.isNull(draft) || !draft.enabled()) {
            return null;
        }
        if (Objects.isNull(draft.supervisor()) || Objects.isNull(draft.supervisor().agentId())) {
            issues.add(issue("SUPERVISOR_REQUIRED", "definition.collaboration.supervisor", "协同模式必须选择Supervisor Agent"));
            return null;
        }
        if (draft.members().isEmpty() || draft.members().size() > MAX_MEMBER_AGENTS) {
            issues.add(issue("MEMBER_COUNT_INVALID", "definition.collaboration.members", "成员Agent数量必须在" + MIN_MEMBER_AGENTS + "到" + MAX_MEMBER_AGENTS + "之间"));
        }
        AgentCollaborationLimits limits = resolveLimits(draft.limits(), issues);
        ApplicationAgentReference supervisor = resolveCollaborationAgent(draft.supervisor(), null, AgentType.SUPERVISOR, "definition.collaboration.supervisor", issues);

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
        AgentReferenceService.AgentReference agent = agentReferenceService.findAgent(reference.agentId()).orElse(null);
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
        // 草稿只关联 Agent 主体，Application 发布时统一锁定当前正式版本。
        Long versionId = Objects.requireNonNullElse(lockedVersionId, agent.publishedVersionId());
        if (Objects.isNull(versionId)) {
            issues.add(issue("COLLABORATION_AGENT_UNPUBLISHED", field, "协作Agent尚无生效版本"));
            return null;
        }
        Long resolvedVersionId = agentReferenceService.findVersion(reference.agentId(), versionId).orElse(null);
        if (Objects.isNull(resolvedVersionId)) {
            issues.add(issue("COLLABORATION_AGENT_VERSION_NOT_FOUND", field, "协作Agent版本不存在或不属于所选Agent"));
            return null;
        }
        AgentReferenceService.AgentIdentity identity = agent.identity();
        return new ApplicationAgentReference(identity.id(), resolvedVersionId, identity.name(), identity.description());
    }

    private AgentNodeConfig resolveAgentNode(
            String nodeId,
            AgentNodeConfig config,
            List<ApplicationValidationIssueVO> issues) {
        Long agentId = config.agentId();
        AgentReferenceService.AgentReference agent =
                agentReferenceService.findAgent(agentId).orElse(null);
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
                agentReferenceService.findVersion(agentId, versionId).orElse(null);
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

    /**
     * 发布校验内部的快速路由解析结果。
     *
     * @param binding 已锁定意图树版本的快速路由配置
     * @param embeddingModelInstanceId 意图树正式版本冻结的Embedding模型实例主键
     */
    private record FastRouteResolution(
            ApplicationIntentBinding binding,
            Long embeddingModelInstanceId) {
    }
}

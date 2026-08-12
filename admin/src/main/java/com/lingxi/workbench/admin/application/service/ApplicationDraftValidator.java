package com.lingxi.workbench.admin.application.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentType;
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
import com.lingxi.workbench.infra.ai.variable.RuntimeVariableCatalog;
import com.lingxi.workbench.infra.ai.variable.common.enums.BuiltInRuntimeVariable;
import com.lingxi.workbench.infra.ai.variable.common.enums.RuntimeVariableChannel;
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
import com.lingxi.workbench.workflow.model.FallbackAgent;
import com.lingxi.workbench.workflow.model.CapabilityRiskLevel;
import com.lingxi.workbench.workflow.model.InteractionRecordMemoryConfig;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import com.lingxi.workbench.workflow.model.UserProfileMemoryConfig;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import com.lingxi.workbench.workflow.model.intent.IntentNodeDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentNodeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;
import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.MAX_MEMBER_AGENTS;
import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.MIN_DELEGATIONS;
import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.MIN_MEMBER_AGENTS;
import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.SUPERVISOR_MAX_DEPTH;
import static com.lingxi.workbench.infra.ai.agent.common.constants.AgentSchemaConstants.AGENT_SCHEMA_VERSION;

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

    private final AgentReferenceService agentReferenceService;
    private final ApplicationIntentTreeReferenceGateway intentTreeReferenceGateway;
    private final WorkflowService workflowService;
    private final ModelInstanceService modelInstanceService;
    private final PromptService promptService;
    private final ToolService toolService;

    /**
     * 校验当前协议草稿并锁定全部资源版本，返回可发布的不可变定义。
     */
    public ApplicationDefinitionResolution resolveForPublish(ApplicationDefinition draftDefinition) {
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

        AgentCollaborationDefinition collaboration = resolveCollaboration(draftDefinition.collaboration(), issues);
        ApplicationContextPolicy contextPolicy = resolveContextPolicy(draftDefinition.contextPolicy(), issues);
        CapabilityCatalogSnapshot capabilityCatalog = resolveCapabilityCatalog(
                draftDefinition.capabilityCatalog(), issues);
        ApplicationCapabilityDefinition fallbackCapability = resolveFallback(
                draftDefinition.fallback(), capabilityCatalog, issues);
        validateAgentCapabilityTopology(fallbackCapability, capabilityCatalog, issues);
        FastRouteResolution fastRouteResolution = resolveFastRoutes(
                draftDefinition.optionalFastRoutes(), capabilityCatalog, issues);
        MemoryConfig memoryConf = resolveMemory(draftDefinition.memoryConf(), fastRouteResolution.embeddingModelInstanceId(), issues);
        ApplicationRuntimePolicy runtimePolicy = resolveRuntimePolicy(draftDefinition.runtimePolicy(), issues);
        validateRuntimeBoundaries(
                runtimePolicy, capabilityCatalog, collaboration, contextPolicy, issues);
        if (!issues.isEmpty()) {
            return new ApplicationDefinitionResolution(null, issues);
        }
        ApplicationDefinition resolved = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .collaboration(collaboration)
                .optionalFastRoutes(fastRouteResolution.binding())
                .memoryConf(memoryConf)
                .contextPolicy(contextPolicy)
                .fallback(new FallbackAgent(fallbackCapability.getCode()))
                .capabilityCatalog(capabilityCatalog)
                .runtimePolicy(runtimePolicy)
                .build();
        return new ApplicationDefinitionResolution(resolved, issues);
    }

    /** 解析并锁定能力目录中的全部执行目标。 */
    private CapabilityCatalogSnapshot resolveCapabilityCatalog(CapabilityCatalogSnapshot draft,
                                                               List<ApplicationValidationIssueVO> issues) {
        List<ApplicationCapabilityDefinition> resolved = new ArrayList<>();
        Set<String> codes = new HashSet<>();
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

    /** fallback 必须唯一指向已解析的 REACT 或 PLAN_EXECUTOR Agent 能力。 */
    private ApplicationCapabilityDefinition resolveFallback(
            FallbackAgent fallback,
            CapabilityCatalogSnapshot catalog,
            List<ApplicationValidationIssueVO> issues) {
        String field = "definition.fallback.cap";
        String cap = Objects.isNull(fallback) ? null : fallback.cap();
        if (isBlank(cap) || !CAPABILITY_CODE_PATTERN.matcher(cap).matches()) {
            issues.add(issue("FALLBACK_AGENT_REQUIRED", field, "请选择能力目录中的fallback Agent能力"));
            return null;
        }
        List<ApplicationCapabilityDefinition> matches = catalog.capabilities().stream()
                .filter(capability -> cap.equals(capability.getCode()))
                .toList();
        if (matches.size() != 1 || matches.getFirst().getType() != ApplicationCapabilityType.AGENT) {
            issues.add(issue("FALLBACK_AGENT_UNAVAILABLE", field, "fallback必须唯一指向能力目录中的AGENT能力"));
            return null;
        }
        ApplicationCapabilityDefinition capability = matches.getFirst();
        AgentReferenceService.AgentReference agent = agentReferenceService.findAgent(capability.getTargetId()).orElse(null);
        if (Objects.isNull(agent)
                || (agent.identity().agentType() != AgentType.REACT
                && agent.identity().agentType() != AgentType.PLAN_EXECUTOR)) {
            issues.add(issue("FALLBACK_AGENT_TYPE_INVALID", field, "fallback Agent类型只能是REACT或PLAN_EXECUTOR"));
            return null;
        }
        return capability;
    }

    /** V2 只允许 fallback 使用唯一一个 Plan Executor，其他 Agent 能力必须是可编排的 ReAct 叶子。 */
    private void validateAgentCapabilityTopology(
            ApplicationCapabilityDefinition fallback,
            CapabilityCatalogSnapshot catalog,
            List<ApplicationValidationIssueVO> issues) {
        for (ApplicationCapabilityDefinition capability : catalog.capabilities()) {
            if (capability.getType() != ApplicationCapabilityType.AGENT) {
                continue;
            }
            AgentReferenceService.AgentReference agent = agentReferenceService.findAgent(capability.getTargetId()).orElse(null);
            if (Objects.nonNull(agent)
                    && agent.identity().agentType() == AgentType.PLAN_EXECUTOR
                    && (Objects.isNull(fallback) || !Objects.equals(capability.getCode(), fallback.getCode()))) {
                issues.add(issue(
                        "PLAN_EXECUTOR_NESTING_FORBIDDEN",
                        "definition.capabilityCatalog.capabilities",
                        "PLAN_EXECUTOR只能作为Application fallback，不能作为扩展能力或计划叶子"));
            }
        }
    }

    private ApplicationCapabilityDefinition resolveCapability(ApplicationCapabilityDefinition draft, String field, Set<String> codes,
                                                              List<ApplicationValidationIssueVO> issues) {
        if (Objects.isNull(draft) || isBlank(draft.getCode()) || !CAPABILITY_CODE_PATTERN.matcher(draft.getCode()).matches()) {
            issues.add(issue("CAPABILITY_CODE_INVALID", field + ".code", "能力编码需使用2到64位小写字母、数字或下划线"));
            return null;
        }
        if (!codes.add(draft.getCode())) {
            issues.add(issue("CAPABILITY_CODE_DUPLICATED", field + ".code", "能力编码不能重复"));
            return null;
        }
        if (Objects.isNull(draft.getType()) || Objects.isNull(draft.getTargetId())) {
            issues.add(issue("CAPABILITY_TARGET_REQUIRED", field, "请选择能力类型和目标资源"));
            return null;
        }
        validateCapabilityPolicy(draft, field, issues);
        return switch (draft.getType()) {
            case AGENT -> resolveAgentCapability(draft, field, issues);
            case WORKFLOW -> resolveWorkflowCapability(draft, field, issues);
            case TOOL -> resolveToolCapability(draft, field, issues);
        };
    }

    private static void validateCapabilityPolicy(ApplicationCapabilityDefinition capability, String field,
                                                 List<ApplicationValidationIssueVO> issues) {
        if (Objects.nonNull(capability.getDescription()) && capability.getDescription().trim().length() > 500) {
            issues.add(issue("CAPABILITY_DESCRIPTION_TOO_LONG", field + ".description", "能力描述不能超过500个字符"));
        }
        validateOptionalRange(capability.getTimeoutSeconds(), 1, 600, "CAPABILITY_TIMEOUT_INVALID",
                field + ".timeoutSeconds", "能力超时时间", issues);
        validateOptionalRange(capability.getMaxCalls(), 1, 20, "CAPABILITY_CALLS_INVALID",
                field + ".maxCalls", "能力调用次数", issues);
        validateOptionalRange(capability.getResultTokenBudget(), 128, 32000, "CAPABILITY_RESULT_BUDGET_INVALID",
                field + ".resultTokenBudget", "能力结果Token预算", issues);
    }

    private ApplicationCapabilityDefinition resolveAgentCapability(ApplicationCapabilityDefinition draft, String field,
                                                                   List<ApplicationValidationIssueVO> issues) {
        AgentReferenceService.AgentReference agent = agentReferenceService.findAgent(draft.getTargetId()).orElse(null);
        if (Objects.isNull(agent) || !agent.active()
                || (agent.identity().agentType() != AgentType.REACT
                && agent.identity().agentType() != AgentType.PLAN_EXECUTOR)) {
            issues.add(issue("CAPABILITY_AGENT_UNAVAILABLE", field + ".targetId", "能力目标必须是已启用的REACT或PLAN_EXECUTOR Agent"));
            return null;
        }
        Long versionId = Objects.requireNonNullElse(draft.getTargetVersionId(), agent.publishedVersionId());
        AgentReferenceService.AgentVersionReference version = agentReferenceService.findVersion(draft.getTargetId(), versionId).orElse(null);
        if (Objects.isNull(version)
                || version.schemaVersion() != AGENT_SCHEMA_VERSION
                || version.agentType() != agent.identity().agentType()) {
            issues.add(issue("CAPABILITY_AGENT_VERSION_UNAVAILABLE", field + ".targetVersionId", "能力目标Agent没有可用正式版本"));
            return null;
        }
        return agentCapability(
                draft, agent.identity().name(), normalizedDescription(draft, agent.identity().description()), version.id());
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

    private static ApplicationCapabilityDefinition agentCapability(ApplicationCapabilityDefinition draft, String name, String description,
                                                                    Long versionId) {
        return baseCapability(draft, name, description, versionId, queryInputSchema(), responseOutputSchema(),
                "AGENT_VERSION", checksum(versionId));
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

    private static ApplicationRuntimePolicy resolveRuntimePolicy(ApplicationRuntimePolicy draft, List<ApplicationValidationIssueVO> issues) {
        ApplicationRuntimePolicy policy = Objects.requireNonNullElseGet(draft, ApplicationRuntimePolicy::defaults);
        validateRange(policy.getMaxModelCalls(), 1, 100, "RUNTIME_MODEL_CALLS_INVALID", "definition.runtimePolicy.maxModelCalls", "模型调用次数", issues);
        validateRange(policy.getMaxToolCalls(), 0, 100, "RUNTIME_TOOL_CALLS_INVALID", "definition.runtimePolicy.maxToolCalls", "Tool调用次数", issues);
        validateRange(policy.getMaxDelegations(), 0, 10, "RUNTIME_DELEGATIONS_INVALID", "definition.runtimePolicy.maxDelegations", "委派次数", issues);
        validateRange(policy.getTotalTokenBudget(), 1024, 1000000, "RUNTIME_TOKEN_BUDGET_INVALID", "definition.runtimePolicy.totalTokenBudget", "总Token预算", issues);
        validateRange(policy.effectiveTokenGracePercent(), 0, 30, "RUNTIME_TOKEN_GRACE_INVALID", "definition.runtimePolicy.tokenGracePercent", "Token收口缓冲比例", issues);
        validateRange(policy.getTimeoutSeconds(), 1, 3600, "RUNTIME_TIMEOUT_INVALID", "definition.runtimePolicy.timeoutSeconds", "总超时时间", issues);
        return ApplicationRuntimePolicy.builder()
                .maxModelCalls(policy.getMaxModelCalls())
                .maxToolCalls(policy.getMaxToolCalls())
                .maxDelegations(policy.getMaxDelegations())
                .totalTokenBudget(policy.getTotalTokenBudget())
                .tokenGracePercent(policy.effectiveTokenGracePercent())
                .timeoutSeconds(policy.getTimeoutSeconds())
                .build();
    }

    /** Application总预算必须覆盖每个能力的局部边界和已启用协作。 */
    private static void validateRuntimeBoundaries(ApplicationRuntimePolicy runtimePolicy,
                                                  CapabilityCatalogSnapshot catalog,
                                                  AgentCollaborationDefinition collaboration,
                                                  ApplicationContextPolicy contextPolicy,
                                                  List<ApplicationValidationIssueVO> issues) {
        if (Objects.isNull(runtimePolicy) || Objects.isNull(catalog)) {
            return;
        }
        if (Objects.nonNull(collaboration) && runtimePolicy.getMaxDelegations() < MIN_DELEGATIONS) {
            issues.add(issue("RUNTIME_DELEGATIONS_REQUIRED", "definition.runtimePolicy.maxDelegations",
                    "启用多Agent协作时最大委派次数至少为" + MIN_DELEGATIONS));
        }
        int minimumModelCalls = 1;
        if (contextPolicy.contextualization().isEnabled()) {
            minimumModelCalls++;
        }
        if (runtimePolicy.getMaxModelCalls() < minimumModelCalls) {
            issues.add(issue("RUNTIME_MODEL_CALLS_INSUFFICIENT", "definition.runtimePolicy.maxModelCalls",
                    "当前上下文化与Agent执行至少需要" + minimumModelCalls + "次模型调用额度"));
        }
        for (int index = 0; index < catalog.capabilities().size(); index++) {
            ApplicationCapabilityDefinition capability = catalog.capabilities().get(index);
            String field = "definition.capabilityCatalog.capabilities[" + index + "]";
            if (capability.getTimeoutSeconds() > runtimePolicy.getTimeoutSeconds()) {
                issues.add(issue("CAPABILITY_TIMEOUT_EXCEEDS_RUNTIME", field + ".timeoutSeconds",
                        "能力超时时间不能超过Application总超时"));
            }
            if (capability.getResultTokenBudget() > runtimePolicy.getTotalTokenBudget()) {
                issues.add(issue("CAPABILITY_RESULT_BUDGET_EXCEEDS_RUNTIME", field + ".resultTokenBudget",
                        "能力结果Token预算不能超过Application总Token预算"));
            }
        }
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

    /**
     * 校验并锁定可选快速路由；只要求被选中的叶子映射到已发布能力。
     */
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
                : ApplicationCapabilityType.AGENT;
        ApplicationCapabilityDefinition capability = findFastRouteCapability(mapping, expectedType, capabilityCatalog);
        if (Objects.isNull(capability)) {
            issues.add(issue("FAST_ROUTE_CAPABILITY_UNAVAILABLE", field + ".capabilityCode", "请选择能力目录中唯一可用的执行能力"));
            return null;
        }
        if (capability.getType() == ApplicationCapabilityType.AGENT) {
            AgentReferenceService.AgentReference agent = agentReferenceService.findAgent(capability.getTargetId()).orElse(null);
            if (Objects.isNull(agent) || agent.identity().agentType() != AgentType.REACT) {
                issues.add(issue("FAST_ROUTE_AGENT_TYPE_INVALID", field + ".capabilityCode", "快速路由只能指向REACT Agent能力"));
                return null;
            }
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

    /**
     * 分别校验三类记忆配置，交互记录在启用快速路由时复用意图树向量模型。
     */
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
        validateMemoryModel(draft.getChatModelInstanceId(), ModelType.CHAT, "definition.memoryConf.profile.chatModelInstanceId", issues);
        validateMemoryPrompt(draft.getPromptVersionId(), PromptRole.SYSTEM, "definition.memoryConf.profile.promptVersionId", issues);
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
                        "definition.memoryConf.interactionRecord.embeddingModelInstanceId",
                        "交互记录Embedding模型必须与意图树正式版本一致"));
            }
            embeddingModelInstanceId = intentEmbeddingModelInstanceId;
        }
        validateMemoryModel(embeddingModelInstanceId, ModelType.EMBEDDING,
                "definition.memoryConf.interactionRecord.embeddingModelInstanceId", issues);
        validateMemoryModel(draft.getChatModelInstanceId(), ModelType.CHAT,
                "definition.memoryConf.interactionRecord.chatModelInstanceId", issues);
        validateMemoryPrompt(draft.getPromptVersionId(), PromptRole.SYSTEM,
                "definition.memoryConf.interactionRecord.promptVersionId", issues);
        if (draft.effectiveTopK() < 1 || draft.effectiveTopK() > 10) {
            issues.add(issue("MEMORY_TOP_K_INVALID", "definition.memoryConf.interactionRecord.topK",
                    "交互记录检索条数必须在1到10之间"));
        }
        validateRange(draft.effectiveRetentionDays(), MIN_MEMORY_RETENTION_DAYS, MAX_MEMORY_RETENTION_DAYS,
                "MEMORY_RETENTION_DAYS_INVALID", "definition.memoryConf.interactionRecord.retentionDays", "交互记录保留天数", issues);
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
                "definition.memoryConf.summary.chatModelInstanceId", issues);
        validateMemoryPrompt(draft.getPromptVersionId(), PromptRole.SYSTEM,
                "definition.memoryConf.summary.promptVersionId", issues);
        if (draft.effectiveMinRounds() < 4 || draft.effectiveMinRounds() > 50) {
            issues.add(issue("MEMORY_SUMMARY_ROUNDS_INVALID", "definition.memoryConf.summary.minRounds",
                    "摘要阈值必须在4到50轮之间"));
        }
        validateRange(draft.effectiveMinTokens(), MIN_SUMMARY_TRIGGER_TOKENS, MAX_SUMMARY_TRIGGER_TOKENS,
                "MEMORY_SUMMARY_TOKENS_INVALID", "definition.memoryConf.summary.minTokens", "摘要Token阈值", issues);
        return ConversationSummaryMemoryConfig.builder()
                .enabled(true)
                .chatModelInstanceId(draft.getChatModelInstanceId())
                .promptVersionId(draft.getPromptVersionId())
                .minRounds(draft.effectiveMinRounds())
                .minTokens(draft.effectiveMinTokens())
                .build();
    }

    /**
     * 校验上下文化引用和各上下文分区预算，生成可直接冻结的规范配置。
     */
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
            return;
        }
        for (PromptService.PromptVariableReference definition : prompt.variables()) {
            Optional<BuiltInRuntimeVariable> variable = RuntimeVariableCatalog.find(definition.name());
            if (variable.isEmpty()) {
                issues.add(issue("CONTEXTUALIZATION_PROMPT_VARIABLE_UNSUPPORTED", field, "上下文化Prompt包含运行端不支持的变量：" + definition.name()));
                continue;
            }
            if (!variable.get().supportsChannel(RuntimeVariableChannel.CONTEXTUALIZATION_PROMPT)) {
                issues.add(issue("CONTEXTUALIZATION_PROMPT_VARIABLE_UNSAFE", field, "问题上下文化提示词禁止使用变量：" + definition.name()));
            }
            if (Objects.isNull(definition.type()) || !variable.get().getType().getCode().equals(definition.type().getCode())) {
                issues.add(issue("CONTEXTUALIZATION_PROMPT_VARIABLE_TYPE_MISMATCH", field, "上下文化Prompt变量类型不匹配：" + definition.name()));
            }
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
     * 将草稿中的稳定 Agent 主键解析成不可变版本引用，并验证 Supervisor 拓扑。
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
            ApplicationAgentReference resolved = resolveCollaborationAgent(member, null, AgentType.REACT, field, issues);
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
                    : "成员只能选择ReAct Agent";
            issues.add(issue("COLLABORATION_AGENT_TYPE_INVALID", field, message));
            return null;
        }
        // 草稿只关联 Agent 主体，Application 发布时统一锁定当前正式版本。
        Long versionId = Objects.requireNonNullElse(lockedVersionId, agent.publishedVersionId());
        if (Objects.isNull(versionId)) {
            issues.add(issue("COLLABORATION_AGENT_UNPUBLISHED", field, "协作Agent尚无生效版本"));
            return null;
        }
        AgentReferenceService.AgentVersionReference resolvedVersion =
                agentReferenceService.findVersion(reference.agentId(), versionId).orElse(null);
        if (Objects.isNull(resolvedVersion)
                || resolvedVersion.schemaVersion() != AGENT_SCHEMA_VERSION
                || resolvedVersion.agentType() != expectedType) {
            issues.add(issue("COLLABORATION_AGENT_VERSION_NOT_FOUND", field, "协作Agent版本不存在或不属于所选Agent"));
            return null;
        }
        AgentReferenceService.AgentIdentity identity = agent.identity();
        return new ApplicationAgentReference(identity.id(), resolvedVersion.id(), identity.name(), identity.description());
    }

    private static ApplicationValidationIssueVO issue(
            String code, String field, String message) {
        return new ApplicationValidationIssueVO(code, field, message);
    }

    /**
     * 发布校验内部的快速路由解析结果。
     *
     * @param binding                  已锁定意图树版本的快速路由配置
     * @param embeddingModelInstanceId 意图树正式版本冻结的Embedding模型实例主键
     */
    private record FastRouteResolution(
            ApplicationIntentBinding binding,
            Long embeddingModelInstanceId) {
    }
}

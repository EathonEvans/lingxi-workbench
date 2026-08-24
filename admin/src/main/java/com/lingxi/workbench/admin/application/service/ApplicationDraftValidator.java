package com.lingxi.workbench.admin.application.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.admin.agent.service.AgentReferenceService;
import com.lingxi.workbench.admin.application.model.vo.ApplicationValidationIssueVO;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.domain.model.entity.DomainEntity;
import com.lingxi.workbench.admin.domain.repo.DomainRepo;
import com.lingxi.workbench.admin.model.common.enums.ModelType;
import com.lingxi.workbench.admin.model.model.bo.ModelInstanceReferenceBO;
import com.lingxi.workbench.admin.model.service.ModelInstanceService;
import com.lingxi.workbench.admin.prompt.service.PromptService;
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
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.workflow.model.ConversationSummaryMemoryConfig;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import com.lingxi.workbench.workflow.model.ContextBudgetPreset;
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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;
import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.MAX_RESOLVED_CAPABILITIES;
import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.MIN_DELEGATIONS;
import static com.lingxi.workbench.infra.ai.agent.common.constants.AgentSchemaConstants.AGENT_SCHEMA_VERSION;
import static com.lingxi.workbench.workflow.common.constants.IntentRoutingConstants.INTENT_TREE_SCHEMA_VERSION;

/**
 * Application 发布校验与引用解析。
 *
 * <p>草稿允许 AgentVersion 为空；发布快照必须解析并锁定不可变版本。</p>
 */
@Component
@RequiredArgsConstructor
public class ApplicationDraftValidator {
    private static final String DEFAULT_AGENT_CAPABILITY_CODE = "default_agent";
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
    private final DomainRepo domainRepo;

    /**
     * 校验当前协议草稿并锁定全部资源版本，返回可发布的不可变定义。
     */
    public ApplicationDefinitionResolution resolveForPublish(ApplicationDefinition draftDefinition, Long domainId) {
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

        DomainEntity root = resolveRootDomain(domainId, issues);
        AgentCollaborationDefinition collaboration = resolveCollaboration(draftDefinition.collaboration(), root, issues);
        ApplicationContextPolicy contextPolicy = resolveContextPolicy(draftDefinition.contextPolicy(), issues);
        ApplicationRuntimePolicy runtimePolicy = resolveRuntimePolicy(draftDefinition.runtimePolicy(), issues);
        CapabilityCatalogSnapshot explicitCapabilities = resolveCapabilityCatalog(
                draftDefinition.capabilityCatalog(), runtimePolicy.getMaxDelegations(), issues);
        CapabilityCatalogSnapshot capabilityCatalog = mergeResolvedCapabilities(
                collaboration, explicitCapabilities, runtimePolicy.getMaxDelegations(), issues);
        IntentRoutingResolution intentRoutingResolution = resolveIntentRouting(
                draftDefinition.intentRouting(), capabilityCatalog, root, issues);
        MemoryConfig memoryConf = resolveMemory(draftDefinition.memoryConf(), intentRoutingResolution.embeddingModelInstanceId(), issues);
        validateRuntimeBoundaries(
                runtimePolicy, capabilityCatalog, collaboration, contextPolicy, issues);
        if (!issues.isEmpty()) {
            return new ApplicationDefinitionResolution(null, issues);
        }
        ApplicationDefinition resolved = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .collaboration(collaboration)
                .intentRouting(intentRoutingResolution.binding())
                .memoryConf(memoryConf)
                .contextPolicy(contextPolicy)
                .fallback(new FallbackAgent(DEFAULT_AGENT_CAPABILITY_CODE))
                .capabilityCatalog(capabilityCatalog)
                .runtimePolicy(runtimePolicy)
                .build();
        return new ApplicationDefinitionResolution(resolved, issues);
    }

    /** 解析并锁定能力目录中的全部执行目标。 */
    private CapabilityCatalogSnapshot resolveCapabilityCatalog(CapabilityCatalogSnapshot draft, int readOnlyMaxCalls,
                                                               List<ApplicationValidationIssueVO> issues) {
        List<ApplicationCapabilityDefinition> resolved = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        List<ApplicationCapabilityDefinition> capabilities = Objects.isNull(draft) ? List.of() : draft.capabilities();
        if (capabilities.size() > MAX_RESOLVED_CAPABILITIES) {
            issues.add(issue("CAPABILITY_COUNT_INVALID", "definition.capabilityCatalog.capabilities", "能力目录最多配置50项能力"));
            return CapabilityCatalogSnapshot.builder().capabilities(resolved).build();
        }
        for (int index = 0; index < capabilities.size(); index++) {
            ApplicationCapabilityDefinition capability = capabilities.get(index);
            String field = "definition.capabilityCatalog.capabilities[" + index + "]";
            ApplicationCapabilityDefinition item = resolveCapability(
                    capability, field, codes, readOnlyMaxCalls, issues);
            if (Objects.nonNull(item)) {
                resolved.add(item);
            }
        }
        return CapabilityCatalogSnapshot.builder().capabilities(List.copyOf(resolved)).build();
    }

    /** 合并自动生成的 Agent 能力和管理员显式绑定的 Workflow 能力。 */
    private static CapabilityCatalogSnapshot mergeResolvedCapabilities(
            AgentCollaborationDefinition collaboration,
            CapabilityCatalogSnapshot explicitCapabilities,
            int readOnlyMaxCalls,
            List<ApplicationValidationIssueVO> issues) {
        List<ApplicationCapabilityDefinition> capabilities = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        if (Objects.nonNull(collaboration)) {
            addAgentCapability(DEFAULT_AGENT_CAPABILITY_CODE, collaboration.supervisor(), capabilities, codes,
                    readOnlyMaxCalls, issues);
            for (ApplicationAgentReference member : collaboration.members()) {
                addAgentCapability("agent_" + member.agentId(), member, capabilities, codes,
                        readOnlyMaxCalls, issues);
            }
        }
        for (ApplicationCapabilityDefinition capability : explicitCapabilities.capabilities()) {
            if (!codes.add(capability.getCode())) {
                issues.add(issue("CAPABILITY_CODE_DUPLICATED", "definition.capabilityCatalog.capabilities",
                        "Workflow能力编码与系统生成的Agent能力编码冲突：" + capability.getCode()));
                continue;
            }
            capabilities.add(capability);
        }
        if (capabilities.size() > MAX_RESOLVED_CAPABILITIES) {
            issues.add(issue("CAPABILITY_COUNT_INVALID", "definition.capabilityCatalog.capabilities",
                    "发布快照最多包含" + MAX_RESOLVED_CAPABILITIES + "项Agent与Workflow能力，请排除部分领域或Agent"));
        }
        return CapabilityCatalogSnapshot.builder().capabilities(List.copyOf(capabilities)).build();
    }

    private static void addAgentCapability(
            String code,
            ApplicationAgentReference reference,
            List<ApplicationCapabilityDefinition> capabilities,
            Set<String> codes,
            int readOnlyMaxCalls,
            List<ApplicationValidationIssueVO> issues) {
        if (Objects.isNull(reference) || !codes.add(code)) {
            issues.add(issue("CAPABILITY_CODE_DUPLICATED", "definition.collaboration",
                    "系统生成的Agent能力编码冲突：" + code));
            return;
        }
        String description = isBlank(reference.description()) ? reference.name() : reference.description().trim();
        capabilities.add(ApplicationCapabilityDefinition.builder()
                .code(code)
                .type(ApplicationCapabilityType.AGENT)
                .name(reference.name())
                .description(description)
                .targetId(reference.agentId())
                .targetVersionId(reference.agentVersionId())
                .inputSchema(queryInputSchema())
                .outputSchema(responseOutputSchema())
                .implementationType("AGENT_VERSION")
                .configurationChecksum(checksum(reference.agentVersionId()))
                .riskLevel(CapabilityRiskLevel.READ_ONLY)
                .timeoutSeconds(120)
                .maxCalls(Math.max(readOnlyMaxCalls, 1))
                .resultTokenBudget(8_192)
                .build());
    }

    private ApplicationCapabilityDefinition resolveCapability(ApplicationCapabilityDefinition draft, String field,
                                                              Set<String> codes, int readOnlyMaxCalls,
                                                              List<ApplicationValidationIssueVO> issues) {
        if (Objects.isNull(draft) || isBlank(draft.getCode()) || !CAPABILITY_CODE_PATTERN.matcher(draft.getCode()).matches()) {
            issues.add(issue("CAPABILITY_CODE_INVALID", field + ".code", "能力编码需使用2到64位小写字母、数字或下划线"));
            return null;
        }
        if (!codes.add(draft.getCode())) {
            issues.add(issue("CAPABILITY_CODE_DUPLICATED", field + ".code", "能力编码不能重复"));
            return null;
        }
        if (draft.getType() != ApplicationCapabilityType.WORKFLOW) {
            issues.add(issue("CAPABILITY_TYPE_MANAGED", field + ".type",
                    "Application仅显式绑定Workflow；Agent能力由领域范围生成，Tool由Agent管理"));
            return null;
        }
        if (Objects.isNull(draft.getTargetId())) {
            issues.add(issue("CAPABILITY_TARGET_REQUIRED", field, "请选择Workflow目标资源"));
            return null;
        }
        validateCapabilityPolicy(draft, field, issues);
        return resolveWorkflowCapability(draft, field, readOnlyMaxCalls, issues);
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
        if (capability.getRiskLevel() != null && capability.getRiskLevel() != CapabilityRiskLevel.READ_ONLY
                && capability.getMaxCalls() != null && capability.getMaxCalls() != 1) {
            issues.add(issue("CAPABILITY_SIDE_EFFECT_CALLS_INVALID", field + ".maxCalls",
                    "写操作和高风险能力的最大调用次数必须为1"));
        }
        validateOptionalRange(capability.getResultTokenBudget(), 128, 32000, "CAPABILITY_RESULT_BUDGET_INVALID",
                field + ".resultTokenBudget", "能力结果Token预算", issues);
    }

    private ApplicationCapabilityDefinition resolveWorkflowCapability(ApplicationCapabilityDefinition draft, String field,
                                                                      int readOnlyMaxCalls,
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
                "WORKFLOW_VERSION", checksum(definition), readOnlyMaxCalls);
    }

    private static ApplicationCapabilityDefinition baseCapability(ApplicationCapabilityDefinition draft, String name, String description,
                                                                  Long versionId, JSONObject inputSchema, JSONObject outputSchema,
                                                                  String implementationType, String configurationChecksum,
                                                                  int readOnlyMaxCalls) {
        CapabilityRiskLevel riskLevel = Objects.requireNonNullElse(
                draft.getRiskLevel(), CapabilityRiskLevel.READ_ONLY);
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
                .riskLevel(riskLevel)
                .timeoutSeconds(defaultIfNull(draft.getTimeoutSeconds(), 120))
                .maxCalls(defaultIfNull(draft.getMaxCalls(), defaultCapabilityMaxCalls(riskLevel, readOnlyMaxCalls)))
                .resultTokenBudget(defaultIfNull(draft.getResultTokenBudget(), 8192))
                .build();
    }

    private static String normalizedDescription(ApplicationCapabilityDefinition draft, String fallback) {
        return isBlank(draft.getDescription()) ? fallback : draft.getDescription();
    }

    private static int defaultCapabilityMaxCalls(CapabilityRiskLevel riskLevel, int readOnlyMaxCalls) {
        return riskLevel == CapabilityRiskLevel.READ_ONLY ? Math.max(readOnlyMaxCalls, 1) : 1;
    }

    private static ApplicationRuntimePolicy resolveRuntimePolicy(ApplicationRuntimePolicy draft, List<ApplicationValidationIssueVO> issues) {
        ApplicationRuntimePolicy policy = Objects.requireNonNullElseGet(draft, ApplicationRuntimePolicy::defaults);
        validateRange(policy.getMaxModelCalls(), 1, 100, "RUNTIME_MODEL_CALLS_INVALID", "definition.runtimePolicy.maxModelCalls", "模型调用次数", issues);
        validateRange(policy.getMaxToolCalls(), 0, 100, "RUNTIME_TOOL_CALLS_INVALID", "definition.runtimePolicy.maxToolCalls", "Tool调用次数", issues);
        validateRange(policy.getMaxDelegations(), 0, 10, "RUNTIME_DELEGATIONS_INVALID", "definition.runtimePolicy.maxDelegations", "委派次数", issues);
        validateRange(policy.getTotalTokenBudget(), 1024, 1000000, "RUNTIME_TOKEN_BUDGET_INVALID", "definition.runtimePolicy.totalTokenBudget", "总Token预算", issues);
        validateRange(policy.effectiveTokenGracePercent(), 0, 30, "RUNTIME_TOKEN_GRACE_INVALID", "definition.runtimePolicy.tokenGracePercent", "Token收口缓冲比例", issues);
        return ApplicationRuntimePolicy.builder()
                .maxModelCalls(policy.getMaxModelCalls())
                .maxToolCalls(policy.getMaxToolCalls())
                .maxDelegations(policy.getMaxDelegations())
                .totalTokenBudget(policy.getTotalTokenBudget())
                .tokenGracePercent(policy.effectiveTokenGracePercent())
                .build();
    }

    /** Application预算必须覆盖能力结果和已启用协作。 */
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
                    "当前问题重写与Agent执行至少需要" + minimumModelCalls + "次模型调用额度"));
        }
        for (int index = 0; index < catalog.capabilities().size(); index++) {
            ApplicationCapabilityDefinition capability = catalog.capabilities().get(index);
            String field = "definition.capabilityCatalog.capabilities[" + index + "]";
            if (capability.getResultTokenBudget() > runtimePolicy.getTotalTokenBudget()) {
                issues.add(issue("CAPABILITY_RESULT_BUDGET_EXCEEDS_RUNTIME", field + ".resultTokenBudget",
                        "能力结果Token预算不能超过Application总Token预算"));
            }
            if (capability.getMaxCalls() > runtimePolicy.getMaxDelegations()) {
                issues.add(issue("CAPABILITY_CALLS_EXCEED_DELEGATIONS", field + ".maxCalls",
                        "能力调用次数不能超过Application委派次数上限"));
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

    private static String checksum(Object value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(JSON.toJSONBytes(value)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少SHA-256算法", exception);
        }
    }

    /**
     * 校验并锁定意图路由；只要求被选中的叶子映射到已发布能力。
     */
    private IntentRoutingResolution resolveIntentRouting(ApplicationIntentBinding draft,
                                                         CapabilityCatalogSnapshot capabilityCatalog,
                                                         DomainEntity root,
                                                         List<ApplicationValidationIssueVO> issues) {
        if (Objects.isNull(draft) || !draft.isEnabled()) {
            return new IntentRoutingResolution(null, null);
        }
        int initialIssueCount = issues.size();
        if (Objects.isNull(draft.getIntentTreeId())) {
            issues.add(issue("INTENT_ROUTING_TREE_REQUIRED", "definition.intentRouting.intentTreeId", "请选择意图树"));
            return new IntentRoutingResolution(null, null);
        }
        ApplicationIntentTreeReferenceGateway.IntentTreeReference tree = intentTreeReferenceGateway
                .findPublishedTree(draft.getIntentTreeId())
                .orElse(null);
        if (Objects.isNull(tree)) {
            issues.add(issue("INTENT_ROUTING_TREE_NOT_FOUND", "definition.intentRouting.intentTreeId", "意图树不存在"));
            return new IntentRoutingResolution(null, null);
        }
        if (!tree.active()) {
            issues.add(issue("INTENT_ROUTING_TREE_DISABLED", "definition.intentRouting.intentTreeId", "意图树已停用"));
        }
        Long treeDomainId = intentTreeReferenceGateway.findDomainId(draft.getIntentTreeId()).orElse(null);
        if (Objects.nonNull(root) && !belongsToRoot(treeDomainId, root.getId())) {
            issues.add(issue("INTENT_ROUTING_TREE_DOMAIN_MISMATCH", "definition.intentRouting.intentTreeId",
                    "意图树不属于Application选择的一级领域"));
        }
        if (Objects.isNull(tree.versionId()) || Objects.isNull(tree.definition())) {
            issues.add(issue("INTENT_ROUTING_TREE_UNPUBLISHED", "definition.intentRouting.intentTreeId", "意图树尚无可用正式版本"));
            return new IntentRoutingResolution(null, null);
        }
        if (tree.definition().schemaVersion() != INTENT_TREE_SCHEMA_VERSION) {
            issues.add(issue("INTENT_ROUTING_TREE_SCHEMA_UNSUPPORTED", "definition.intentRouting.intentTreeId", "意图树生效版本不是Schema V2，请重新发布"));
            return new IntentRoutingResolution(null, tree.definition().embeddingModelInstanceId());
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
            issues.add(issue("INTENT_ROUTING_MAPPING_REQUIRED", "definition.intentRouting.mappings", "请至少配置一个允许确定性直达的叶子"));
        }
        for (int index = 0; index < mappings.size(); index++) {
            ApplicationIntentMapping mapping = mappings.get(index);
            String field = "definition.intentRouting.mappings[" + index + "]";
            if (Objects.isNull(mapping) || !leafCodes.contains(mapping.getIntentCode())) {
                issues.add(issue("INTENT_ROUTING_MAPPING_UNKNOWN", field + ".intentCode", "意图路由必须引用当前正式版本中的启用叶子意图"));
                continue;
            }
            if (!mappedCodes.add(mapping.getIntentCode())) {
                issues.add(issue("INTENT_ROUTING_MAPPING_DUPLICATED", field + ".intentCode", "同一叶子意图不能重复配置路由"));
                continue;
            }
            ApplicationIntentMapping resolved = resolveIntentRoutingTarget(mapping, capabilityCatalog, field, issues);
            if (Objects.nonNull(resolved)) {
                resolvedMappings.add(resolved);
            }
        }
        if (issues.size() > initialIssueCount) {
            return new IntentRoutingResolution(null, tree.definition().embeddingModelInstanceId());
        }
        ApplicationIntentBinding binding = ApplicationIntentBinding.builder()
                .enabled(true)
                .intentTreeId(tree.intentTreeId())
                .intentTreeVersionId(tree.versionId())
                .mappings(resolvedMappings)
                .build();
        return new IntentRoutingResolution(binding, tree.definition().embeddingModelInstanceId());
    }

    private ApplicationIntentMapping resolveIntentRoutingTarget(ApplicationIntentMapping mapping,
                                                                CapabilityCatalogSnapshot capabilityCatalog,
                                                                String field,
                                                                List<ApplicationValidationIssueVO> issues) {
        if (Objects.isNull(mapping.getTargetType())) {
            issues.add(issue("INTENT_ROUTING_TARGET_REQUIRED", field + ".targetType", "请选择意图路由执行目标"));
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
        ApplicationCapabilityDefinition capability = findIntentRoutingCapability(mapping, expectedType, capabilityCatalog);
        if (Objects.isNull(capability)) {
            issues.add(issue("INTENT_ROUTING_CAPABILITY_UNAVAILABLE", field + ".capabilityCode", "请选择能力目录中唯一可用的执行能力"));
            return null;
        }
        if (capability.getType() == ApplicationCapabilityType.AGENT) {
            AgentReferenceService.AgentReference agent = agentReferenceService.findAgent(capability.getTargetId()).orElse(null);
            if (Objects.isNull(agent) || agent.identity().role() != AgentRole.SPECIALIST) {
                issues.add(issue("INTENT_ROUTING_AGENT_ROLE_INVALID", field + ".capabilityCode", "意图路由只能指向领域专家能力"));
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

    private static ApplicationCapabilityDefinition findIntentRoutingCapability(ApplicationIntentMapping mapping, ApplicationCapabilityType expectedType,
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
     * 分别校验三类记忆配置，交互记录在启用意图路由时复用意图树向量模型。
     */
    private MemoryConfig resolveMemory(MemoryConfig draft, Long intentEmbeddingModelInstanceId, List<ApplicationValidationIssueVO> issues) {
        MemoryConfig source = Objects.requireNonNullElseGet(draft, MemoryConfig::disabled);
        boolean chatModelRequired = source.profile().isEnabled()
                || source.interactionRecord().isEnabled()
                || source.summary().isEnabled();
        Long chatModelInstanceId = chatModelRequired ? source.getChatModelInstanceId() : null;
        if (chatModelRequired) {
            validateMemoryModel(chatModelInstanceId, ModelType.CHAT,
                    "definition.memoryConf.chatModelInstanceId", issues);
        }

        Long embeddingModelInstanceId = null;
        if (source.interactionRecord().isEnabled()) {
            embeddingModelInstanceId = source.getEmbeddingModelInstanceId();
            if (Objects.nonNull(intentEmbeddingModelInstanceId)) {
                if (Objects.nonNull(embeddingModelInstanceId)
                        && !Objects.equals(embeddingModelInstanceId, intentEmbeddingModelInstanceId)) {
                    issues.add(issue("MEMORY_EMBEDDING_INTENT_MISMATCH",
                            "definition.memoryConf.embeddingModelInstanceId",
                            "交互记录Embedding模型必须与意图树正式版本一致"));
                }
                embeddingModelInstanceId = intentEmbeddingModelInstanceId;
            }
            validateMemoryModel(embeddingModelInstanceId, ModelType.EMBEDDING,
                    "definition.memoryConf.embeddingModelInstanceId", issues);
        }
        return MemoryConfig.builder()
                .chatModelInstanceId(chatModelInstanceId)
                .embeddingModelInstanceId(embeddingModelInstanceId)
                .profile(resolveProfileMemory(source.profile(), issues))
                .interactionRecord(resolveInteractionMemory(source.interactionRecord(), issues))
                .summary(resolveSummaryMemory(source.summary(), issues))
                .build();
    }

    private UserProfileMemoryConfig resolveProfileMemory(UserProfileMemoryConfig draft, List<ApplicationValidationIssueVO> issues) {
        if (!draft.isEnabled()) {
            return UserProfileMemoryConfig.disabled();
        }
        validateMemoryPrompt(draft.getPromptVersionId(), PromptRole.SYSTEM, "definition.memoryConf.profile.promptVersionId", issues);
        return UserProfileMemoryConfig.builder()
                .enabled(true)
                .promptVersionId(draft.getPromptVersionId())
                .updateStrategy(draft.effectiveUpdateStrategy())
                .build();
    }

    private InteractionRecordMemoryConfig resolveInteractionMemory(InteractionRecordMemoryConfig draft,
                                                                   List<ApplicationValidationIssueVO> issues) {
        if (!draft.isEnabled()) {
            return InteractionRecordMemoryConfig.disabled();
        }
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
                .promptVersionId(draft.getPromptVersionId())
                .topK(draft.effectiveTopK())
                .retentionDays(draft.effectiveRetentionDays())
                .build();
    }

    private ConversationSummaryMemoryConfig resolveSummaryMemory(ConversationSummaryMemoryConfig draft, List<ApplicationValidationIssueVO> issues) {
        if (!draft.isEnabled()) {
            return ConversationSummaryMemoryConfig.disabled();
        }
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
                .promptVersionId(draft.getPromptVersionId())
                .minRounds(draft.effectiveMinRounds())
                .minTokens(draft.effectiveMinTokens())
                .build();
    }

    /**
     * 校验问题重写引用和各上下文分区预算，生成可直接冻结的规范配置。
     */
    private ApplicationContextPolicy resolveContextPolicy(ApplicationContextPolicy draft, List<ApplicationValidationIssueVO> issues) {
        ApplicationContextPolicy source = Objects.requireNonNullElseGet(draft, ApplicationContextPolicy::defaults);
        ContextualizationPolicy contextualization = resolveContextualization(source.contextualization(), issues);
        ContextBudgetPreset preset = source.preset();
        ContextBudgetPolicy sourceBudget = preset == ContextBudgetPreset.CUSTOM ? source.getBudget() : preset.toBudget();
        if (preset == ContextBudgetPreset.CUSTOM && Objects.isNull(sourceBudget)) {
            issues.add(issue("CONTEXT_CUSTOM_BUDGET_REQUIRED", "definition.contextPolicy.budget",
                    "自定义上下文模式必须配置完整Token分区"));
            sourceBudget = ContextBudgetPolicy.defaults();
        }
        ContextBudgetPolicy budget = resolveContextBudget(sourceBudget, issues);
        return ApplicationContextPolicy.builder()
                .contextualization(contextualization)
                .preset(preset)
                .budget(budget)
                .build();
    }

    private ContextualizationPolicy resolveContextualization(ContextualizationPolicy draft, List<ApplicationValidationIssueVO> issues) {
        if (!draft.isEnabled()) {
            return ContextualizationPolicy.disabled();
        }
        validateContextualizationModel(draft.getChatModelInstanceId(), issues);
        validateContextualizationPrompt(draft.getPromptVersionId(), issues);
        validateRange(draft.effectiveMaxOutputTokens(), MIN_CONTEXTUALIZATION_OUTPUT_TOKENS, MAX_CONTEXTUALIZATION_OUTPUT_TOKENS,
                "CONTEXTUALIZATION_OUTPUT_TOKENS_INVALID", "definition.contextPolicy.contextualization.maxOutputTokens", "问题重写输出Token上限", issues);
        validateRange(draft.effectiveMaxOutputCharacters(), MIN_CONTEXTUALIZATION_OUTPUT_CHARACTERS, MAX_CONTEXTUALIZATION_OUTPUT_CHARACTERS,
                "CONTEXTUALIZATION_OUTPUT_CHARACTERS_INVALID", "definition.contextPolicy.contextualization.maxOutputCharacters", "问题重写输出字符上限", issues);
        validateRange(draft.effectiveTimeoutSeconds(), 1, MAX_CONTEXTUALIZATION_TIMEOUT_SECONDS,
                "CONTEXTUALIZATION_TIMEOUT_INVALID", "definition.contextPolicy.contextualization.timeoutSeconds", "问题重写超时时间", issues);
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
            issues.add(issue("CONTEXTUALIZATION_MODEL_REQUIRED", field, "请选择问题重写使用的Chat模型"));
            return;
        }
        ModelInstanceReferenceBO model = modelInstanceService.findReference(modelId).orElse(null);
        if (Objects.isNull(model) || model.status() != EnableStatus.ACTIVE || model.modelType() != ModelType.CHAT) {
            issues.add(issue("CONTEXTUALIZATION_MODEL_UNAVAILABLE", field, "问题重写Chat模型不存在、已停用或类型不匹配"));
        }
    }

    private void validateContextualizationPrompt(Long versionId, List<ApplicationValidationIssueVO> issues) {
        String field = "definition.contextPolicy.contextualization.promptVersionId";
        if (Objects.isNull(versionId)) {
            issues.add(issue("CONTEXTUALIZATION_PROMPT_REQUIRED", field, "请选择已发布的问题重写SYSTEM Prompt版本"));
            return;
        }
        PromptService.PromptVersionReference prompt = promptService.findPublishedVersion(versionId).orElse(null);
        if (Objects.isNull(prompt) || !prompt.active() || prompt.role() != PromptRole.SYSTEM) {
            issues.add(issue("CONTEXTUALIZATION_PROMPT_UNAVAILABLE", field, "问题重写Prompt版本不存在、已停用或角色不匹配"));
            return;
        }
        Set<String> variableNames = new HashSet<>();
        List<PromptService.PromptVariableReference> variables = Objects.requireNonNullElse(prompt.variables(), List.of());
        for (PromptService.PromptVariableReference definition : variables) {
            variableNames.add(definition.name());
            Optional<BuiltInRuntimeVariable> variable = RuntimeVariableCatalog.find(definition.name());
            if (variable.isEmpty()) {
                issues.add(issue("CONTEXTUALIZATION_PROMPT_VARIABLE_UNSUPPORTED", field, "问题重写Prompt包含运行端不支持的变量：" + definition.name()));
                continue;
            }
            if (!variable.get().supportsChannel(RuntimeVariableChannel.CONTEXTUALIZATION_PROMPT)) {
                issues.add(issue("CONTEXTUALIZATION_PROMPT_VARIABLE_UNSAFE", field, "问题重写提示词禁止使用变量：" + definition.name()));
            }
            if (Objects.isNull(definition.type()) || !variable.get().getType().getCode().equals(definition.type().getCode())) {
                issues.add(issue("CONTEXTUALIZATION_PROMPT_VARIABLE_TYPE_MISMATCH", field, "问题重写Prompt变量类型不匹配：" + definition.name()));
            }
        }
        requireContextualizationVariable(variableNames, BuiltInRuntimeVariable.USER_QUERY, field, issues);
        requireContextualizationVariable(variableNames, BuiltInRuntimeVariable.CONTEXTUALIZATION_HISTORY, field, issues);
    }

    /** 问题与历史统一由发布 Prompt 注入，避免运行时再按消息角色重复发送。 */
    private static void requireContextualizationVariable(Set<String> variableNames, BuiltInRuntimeVariable variable,
                                                         String field, List<ApplicationValidationIssueVO> issues) {
        if (!variableNames.contains(variable.getCode())) {
            issues.add(issue("CONTEXTUALIZATION_PROMPT_VARIABLE_MISSING", field,
                    "问题重写Prompt必须声明变量：" + variable.getCode()));
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

    /** Application必须选择启用的一级领域。 */
    private DomainEntity resolveRootDomain(Long domainId, List<ApplicationValidationIssueVO> issues) {
        DomainEntity domain = Objects.isNull(domainId) ? null : domainRepo.findById(domainId).orElse(null);
        if (Objects.isNull(domain) || domain.getStatus() != EnableStatus.ACTIVE
                || domain.getDepth() != 1 || !Objects.equals(domain.getId(), domain.getRootId())) {
            issues.add(issue("APP_DOMAIN_INVALID", "domainId", "Application必须选择启用的一级领域"));
            return null;
        }
        return domain;
    }

    /** 全局主Agent可以服务任意领域，领域主Agent必须与Application一级领域一致。 */
    private void validateSupervisorDomain(ApplicationAgentReference supervisor, DomainEntity root,
                                          List<ApplicationValidationIssueVO> issues) {
        if (Objects.isNull(supervisor) || Objects.isNull(root)) {
            return;
        }
        AgentReferenceService.AgentReference agent = agentReferenceService.findAgent(supervisor.agentId()).orElse(null);
        if (Objects.nonNull(agent) && Objects.nonNull(agent.domainId())
                && !Objects.equals(agent.domainId(), root.getId())) {
            issues.add(issue("SUPERVISOR_DOMAIN_MISMATCH", "definition.collaboration.supervisor",
                    "主Agent所属领域与Application不一致"));
        }
    }

    /** 只在配置发布阶段检查逻辑领域归属，不参与Chat运行时发现资源。 */
    private boolean belongsToRoot(Long domainId, Long rootId) {
        DomainEntity domain = Objects.isNull(domainId) ? null : domainRepo.findById(domainId).orElse(null);
        return Objects.nonNull(domain)
                && domain.getStatus() == EnableStatus.ACTIVE
                && Objects.equals(domain.getRootId(), rootId);
    }

    /**
     * 将草稿中的稳定 Agent 主键解析成不可变版本引用，并验证 Supervisor 拓扑。
     */
    private AgentCollaborationDefinition resolveCollaboration(AgentCollaborationDefinition draft, DomainEntity root,
                                                              List<ApplicationValidationIssueVO> issues) {
        int initialIssueCount = issues.size();
        if (Objects.isNull(draft)) {
            issues.add(issue("COLLABORATION_REQUIRED", "definition.collaboration", "请选择主Agent并确认能力范围"));
            return null;
        }
        if (Objects.isNull(draft.supervisor()) || Objects.isNull(draft.supervisor().agentId())) {
            issues.add(issue("SUPERVISOR_REQUIRED", "definition.collaboration.supervisor", "请选择负责规划与汇总的主Agent"));
            return null;
        }
        ApplicationAgentReference supervisor = resolveCollaborationAgent(
                draft.supervisor(), null, AgentRole.SUPERVISOR, "definition.collaboration.supervisor", issues);
        validateSupervisorDomain(supervisor, root, issues);

        List<DomainEntity> domains = Objects.isNull(root) ? List.of() : domainRepo.findActiveByRootId(root.getId());
        Map<Long, DomainEntity> domainById = domains.stream()
                .collect(java.util.stream.Collectors.toMap(DomainEntity::getId, domain -> domain));
        Set<Long> excludedDomainIds = resolveExcludedDomains(draft.excludedDomainIds(), root, domainById, issues);
        Set<Long> allDomainIds = new LinkedHashSet<>(domainById.keySet());
        List<AgentReferenceService.AgentReference> candidates = agentReferenceService.listPublishedSpecialists(allDomainIds);
        Set<Long> excludedAgentIds = resolveExcludedAgents(draft.excludedAgentIds(), candidates, issues);

        List<ApplicationAgentReference> members = new ArrayList<>();
        for (AgentReferenceService.AgentReference candidate : candidates) {
            if (excludedDomainIds.contains(candidate.domainId()) || excludedAgentIds.contains(candidate.identity().id())) {
                continue;
            }
            ApplicationAgentReference reference = new ApplicationAgentReference(candidate.identity().id(), null, null, null);
            ApplicationAgentReference resolved = resolveCollaborationAgent(
                    reference, candidate.publishedVersionId(), AgentRole.SPECIALIST,
                    "definition.collaboration.members", issues);
            if (Objects.nonNull(resolved)) {
                members.add(resolved);
            }
        }
        members.sort(Comparator.comparing(ApplicationAgentReference::name)
                .thenComparing(ApplicationAgentReference::agentId));
        if (issues.size() > initialIssueCount || Objects.isNull(supervisor)) {
            return null;
        }
        return new AgentCollaborationDefinition(
                supervisor,
                members,
                excludedDomainIds.stream().sorted().toList(),
                excludedAgentIds.stream().sorted().toList());
    }

    /** 排除父领域时自动排除其全部后代，避免发布快照出现半开放分支。 */
    private static Set<Long> resolveExcludedDomains(List<Long> requestedIds, DomainEntity root,
                                                    Map<Long, DomainEntity> domainById,
                                                    List<ApplicationValidationIssueVO> issues) {
        Set<Long> excludedIds = new LinkedHashSet<>();
        for (Long domainId : requestedIds) {
            if (Objects.isNull(domainId) || !domainById.containsKey(domainId)) {
                issues.add(issue("EXCLUDED_DOMAIN_INVALID", "definition.collaboration.excludedDomainIds",
                        "排除领域不属于Application一级领域"));
                continue;
            }
            if (Objects.nonNull(root) && Objects.equals(domainId, root.getId())) {
                issues.add(issue("ROOT_DOMAIN_EXCLUSION_FORBIDDEN", "definition.collaboration.excludedDomainIds",
                        "不能排除Application选择的一级领域"));
                continue;
            }
            excludedIds.add(domainId);
        }
        boolean changed;
        do {
            changed = false;
            for (DomainEntity domain : domainById.values()) {
                if (excludedIds.contains(domain.getParentId()) && excludedIds.add(domain.getId())) {
                    changed = true;
                }
            }
        } while (changed);
        return excludedIds;
    }

    private static Set<Long> resolveExcludedAgents(List<Long> requestedIds,
                                                   List<AgentReferenceService.AgentReference> candidates,
                                                   List<ApplicationValidationIssueVO> issues) {
        Set<Long> availableIds = candidates.stream()
                .map(candidate -> candidate.identity().id())
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> excludedIds = new LinkedHashSet<>();
        for (Long agentId : requestedIds) {
            if (Objects.isNull(agentId) || !availableIds.contains(agentId)) {
                issues.add(issue("EXCLUDED_AGENT_INVALID", "definition.collaboration.excludedAgentIds",
                        "排除Agent不是当前领域内可用的专家Agent"));
                continue;
            }
            excludedIds.add(agentId);
        }
        return excludedIds;
    }

    private ApplicationAgentReference resolveCollaborationAgent(ApplicationAgentReference reference, Long lockedVersionId, AgentRole expectedType, String field, List<ApplicationValidationIssueVO> issues) {
        AgentReferenceService.AgentReference agent = agentReferenceService.findAgent(reference.agentId()).orElse(null);
        if (Objects.isNull(agent)) {
            issues.add(issue("COLLABORATION_AGENT_NOT_FOUND", field, "协作Agent不存在"));
            return null;
        }
        if (!agent.active()) {
            issues.add(issue("COLLABORATION_AGENT_DISABLED", field, "协作Agent已停用"));
            return null;
        }
        if (agent.identity().role() != expectedType) {
            String message = expectedType == AgentRole.SUPERVISOR
                    ? "主Agent必须选择SUPERVISOR角色"
                    : "协作成员只能选择领域专家";
            issues.add(issue("COLLABORATION_AGENT_ROLE_INVALID", field, message));
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
                || resolvedVersion.role() != expectedType) {
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
     * 发布校验内部的意图路由解析结果。
     *
     * @param binding                  已锁定意图树版本的意图路由配置
     * @param embeddingModelInstanceId 意图树正式版本冻结的Embedding模型实例主键
     */
    private record IntentRoutingResolution(
            ApplicationIntentBinding binding,
            Long embeddingModelInstanceId) {
    }
}

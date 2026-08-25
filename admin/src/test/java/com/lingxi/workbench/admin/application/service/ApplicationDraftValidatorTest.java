package com.lingxi.workbench.admin.application.service;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.admin.application.model.vo.ApplicationValidationIssueVO;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.domain.model.entity.DomainEntity;
import com.lingxi.workbench.admin.domain.repo.DomainRepo;
import com.lingxi.workbench.admin.model.common.enums.ModelType;
import com.lingxi.workbench.admin.model.model.bo.ModelInstanceReferenceBO;
import com.lingxi.workbench.admin.model.service.ModelInstanceService;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableType;
import com.lingxi.workbench.admin.prompt.service.PromptService;
import com.lingxi.workbench.admin.tool.service.ToolService;
import com.lingxi.workbench.admin.tool.common.enums.ToolSourceType;
import com.lingxi.workbench.admin.tool.model.bo.ToolReferenceBO;
import com.lingxi.workbench.admin.workflow.service.WorkflowService;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.FallbackAgent;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.CapabilityRiskLevel;
import com.lingxi.workbench.workflow.model.ApplicationContextPolicy;
import com.lingxi.workbench.workflow.model.AgentCollaborationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.workflow.model.ConversationSummaryMemoryConfig;
import com.lingxi.workbench.workflow.model.ContextBudgetPreset;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import com.lingxi.workbench.workflow.model.ContextualizationPolicy;
import com.lingxi.workbench.workflow.model.InteractionRecordMemoryConfig;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import com.lingxi.workbench.workflow.model.UserProfileMemoryConfig;
import com.lingxi.workbench.workflow.model.UserProfileUpdateStrategy;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import com.lingxi.workbench.workflow.model.intent.IntentNodeDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentNodeType;
import com.lingxi.workbench.workflow.model.intent.IntentDecisionFailureAction;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;
import static com.lingxi.workbench.workflow.common.constants.IntentRoutingConstants.INTENT_TREE_SCHEMA_VERSION;
import static com.lingxi.workbench.infra.ai.agent.common.constants.AgentSchemaConstants.AGENT_SCHEMA_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApplicationDraftValidatorTest {
    private final StubAgentReferenceGateway gateway = new StubAgentReferenceGateway();
    private final Map<Long, WorkflowService.WorkflowReference> workflows = new HashMap<>();
    private final Map<String, WorkflowDefinition> workflowVersions = new HashMap<>();
    private final Map<Long, ModelInstanceReferenceBO> models = new HashMap<>();
    private final Map<Long, PromptService.PromptVersionReference> prompts = new HashMap<>();
    private final Map<Long, ToolReferenceBO> tools = new HashMap<>();
    private final WorkflowService workflowService = workflowService();
    private final ModelInstanceService modelInstanceService = modelInstanceService();
    private final PromptService promptService = promptService();
    private final ToolService toolService = toolService();
    private final DomainRepo domainRepo = domainRepo();
    private final ApplicationDraftValidator validator =
            new ApplicationDraftValidator(
                    gateway,
                    intentGateway(intentTreeId -> Optional.empty()),
                    workflowService,
                    modelInstanceService,
                    promptService,
                    domainRepo);

    @Test
    void shouldResolveSupervisorAndDomainSpecialists() {
        gateway.addAgent(40L, true, 402L);
        gateway.addVersion(40L, 402L);

        ApplicationDefinitionResolution result = resolve(validator, definition());

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().fallback().cap()).isEqualTo("default_agent");
        assertThat(resolvedCapability(result.definition(), "default_agent").getTargetVersionId())
                .isEqualTo(402L);
        assertThat(result.definition().collaboration().members())
                .extracting(ApplicationAgentReference::agentId)
                .containsExactly(20L);
        assertThat(resolvedCapability(result.definition(), "agent_20").getTargetVersionId()).isEqualTo(201L);
    }

    @Test
    void shouldRejectDisabledSupervisor() {
        gateway.addAgent(40L, false, 401L);

        ApplicationDefinitionResolution result = resolve(validator, definition());

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("COLLABORATION_AGENT_DISABLED");
    }

    @Test
    void shouldExcludeSelectedSpecialistFromPublishedSnapshot() {
        AgentCollaborationDefinition collaboration = new AgentCollaborationDefinition(
                new ApplicationAgentReference(40L, null, null, null), List.of(), List.of(), List.of(20L));

        ApplicationDefinitionResolution result = resolve(validator, fallbackDefinitionBuilder()
                .collaboration(collaboration)
                .build());

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().collaboration().members()).isEmpty();
        assertThat(result.definition().collaboration().excludedAgentIds()).containsExactly(20L);
        assertThat(result.definition().capabilityCatalog().capabilities())
                .extracting(ApplicationCapabilityDefinition::getCode)
                .containsExactly("default_agent");
    }

    @Test
    void shouldRejectSpecialistUsedAsSupervisor() {
        gateway.addAgent(10L, true, 101L, AgentRole.SPECIALIST);
        gateway.addVersion(10L, 101L);
        AgentCollaborationDefinition collaboration = new AgentCollaborationDefinition(
                new ApplicationAgentReference(10L, null, null, null), List.of(), List.of(), List.of());

        ApplicationDefinitionResolution result = resolve(validator, fallbackDefinitionBuilder()
                .collaboration(collaboration)
                .build());

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("COLLABORATION_AGENT_ROLE_INVALID");
    }

    @Test
    void shouldLockPublishedIntentTreeVersionAndValidateLeafMappings() {
        IntentNodeDefinition leaf = IntentNodeDefinition.builder()
                .id("refund-node")
                .code("refund")
                .name("申请退款")
                .description("退款")
                .nodeType(IntentNodeType.LEAF)
                .enabled(true)
                .build();
        IntentNodeDefinition exchangeLeaf = IntentNodeDefinition.builder()
                .id("exchange-node")
                .code("exchange")
                .name("申请换货")
                .description("换货")
                .nodeType(IntentNodeType.LEAF)
                .enabled(true)
                .build();
        IntentTreeDefinition treeDefinition = new IntentTreeDefinition(
                INTENT_TREE_SCHEMA_VERSION,
                88L,
                intentRoutingPolicy(),
                List.of(leaf, exchangeLeaf));
        ApplicationDraftValidator intentValidator = new ApplicationDraftValidator(
                gateway,
                intentGateway(intentTreeId -> Optional.of(new ApplicationIntentTreeReferenceGateway.IntentTreeReference(
                        intentTreeId, true, 200L, treeDefinition))),
                workflowService,
                modelInstanceService,
                promptService,
                domainRepo);
        workflows.put(30L, new WorkflowService.WorkflowReference(30L, true, 300L, "退款流程"));
        workflowVersions.put(key(30L, 300L), workflowDefinition());
        ApplicationIntentBinding binding = ApplicationIntentBinding.builder()
                .enabled(true)
                .intentTreeId(2L)
                .mappings(List.of(ApplicationIntentMapping.builder()
                        .intentCode("refund")
                        .targetType(IntentExecutionTargetType.WORKFLOW)
                        .targetId(30L)
                        .build()))
                .build();

        ApplicationDefinitionResolution result = resolve(intentValidator,
                fallbackDefinitionBuilder()
                        .capabilityCatalog(catalog(
                                ApplicationCapabilityDefinition.builder()
                                        .code("refund_workflow")
                                        .type(ApplicationCapabilityType.WORKFLOW)
                                        .targetId(30L)
                                        .build()))
                        .intentRouting(binding)
                        .build());

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().intentRouting().getIntentTreeVersionId()).isEqualTo(200L);
        assertThat(result.definition().intentRouting().mappings()).singleElement()
                .satisfies(mapping -> {
                    assertThat(mapping.getCapabilityCode()).isEqualTo("refund_workflow");
                    assertThat(mapping.getTargetVersionId()).isEqualTo(300L);
                });
    }

    @Test
    void shouldRejectPublishedIntentTreeWithLegacySchema() {
        IntentTreeDefinition legacyDefinition = new IntentTreeDefinition(
                INTENT_TREE_SCHEMA_VERSION - 1, 88L, intentRoutingPolicy(), List.of());
        ApplicationDraftValidator intentValidator = new ApplicationDraftValidator(
                gateway,
                intentGateway(intentTreeId -> Optional.of(new ApplicationIntentTreeReferenceGateway.IntentTreeReference(
                        intentTreeId, true, 200L, legacyDefinition))),
                workflowService,
                modelInstanceService,
                promptService,
                domainRepo);
        ApplicationIntentBinding binding = ApplicationIntentBinding.builder()
                .enabled(true)
                .intentTreeId(2L)
                .mappings(List.of())
                .build();

        ApplicationDefinitionResolution result = resolve(intentValidator,
                fallbackDefinitionBuilder().intentRouting(binding).build());

        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("INTENT_ROUTING_TREE_SCHEMA_UNSUPPORTED");
    }

    @Test
    void shouldRejectApplicationWithoutExecutionEntry() {
        ApplicationDefinitionResolution result = resolve(validator,
                ApplicationDefinition.builder().schemaVersion(APPLICATION_SCHEMA_VERSION).build());

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .containsExactly("COLLABORATION_REQUIRED");
    }

    @Test
    void shouldRejectLegacyApplicationSchema() {
        ApplicationDefinitionResolution result = resolve(validator,
                ApplicationDefinition.builder().schemaVersion(APPLICATION_SCHEMA_VERSION - 1).build());

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .containsExactly("SCHEMA_VERSION_UNSUPPORTED");
    }

    @Test
    void shouldPublishSupervisorFallbackWithoutIntentTree() {
        gateway.addAgent(40L, true, 401L, AgentRole.SUPERVISOR);
        gateway.addVersion(40L, 401L);
        ApplicationDefinition draft = fallbackDefinitionBuilder().build();

        ApplicationDefinitionResolution result = resolve(validator, draft);

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().schemaVersion()).isEqualTo(APPLICATION_SCHEMA_VERSION);
        assertThat(result.definition().hasCompletePublishedDefinition()).isTrue();
        assertThat(result.definition().intentRouting()).isNull();
        assertThat(result.definition().fallback().cap()).isEqualTo("default_agent");
        assertThat(result.definition().runtimePolicy().getMaxDelegations()).isEqualTo(4);
        assertThat(result.definition().capabilityCatalog().capabilities())
                .filteredOn(capability -> "default_agent".equals(capability.getCode()))
                .singleElement()
                .satisfies(capability -> {
                    assertThat(capability.getCode()).isEqualTo("default_agent");
                    assertThat(capability.getTargetVersionId()).isEqualTo(401L);
                    assertThat(capability.getMaxCalls()).isEqualTo(4);
                });
    }

    @Test
    void shouldDefaultReadOnlyWorkflowCallsToDelegationBudget() {
        workflows.put(30L, new WorkflowService.WorkflowReference(30L, true, 300L, "调研流程"));
        workflowVersions.put(key(30L, 300L), workflowDefinition());
        ApplicationCapabilityDefinition capability = ApplicationCapabilityDefinition.builder()
                .code("research_workflow")
                .type(ApplicationCapabilityType.WORKFLOW)
                .targetId(30L)
                .build();

        ApplicationDefinitionResolution result = resolve(validator, fallbackDefinitionBuilder()
                .capabilityCatalog(catalog(capability))
                .build());

        assertThat(result.valid()).isTrue();
        assertThat(resolvedCapability(result.definition(), "research_workflow").getMaxCalls()).isEqualTo(4);
    }

    @Test
    void shouldRejectRepeatedSideEffectCapability() {
        workflows.put(30L, new WorkflowService.WorkflowReference(30L, true, 300L, "提交流程"));
        workflowVersions.put(key(30L, 300L), workflowDefinition());
        ApplicationCapabilityDefinition capability = ApplicationCapabilityDefinition.builder()
                .code("submit_workflow")
                .type(ApplicationCapabilityType.WORKFLOW)
                .targetId(30L)
                .riskLevel(CapabilityRiskLevel.WRITE)
                .maxCalls(2)
                .build();

        ApplicationDefinitionResolution result = resolve(validator, fallbackDefinitionBuilder()
                .capabilityCatalog(catalog(capability))
                .build());

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("CAPABILITY_SIDE_EFFECT_CALLS_INVALID");
    }

    @Test
    void shouldRejectExplicitSupervisorCapability() {
        gateway.addAgent(40L, true, 401L, AgentRole.SUPERVISOR);
        gateway.addVersion(40L, 401L);
        ApplicationCapabilityDefinition fallbackCap = ApplicationCapabilityDefinition.builder()
                .code("fallback_agent")
                .type(ApplicationCapabilityType.AGENT)
                .targetId(40L)
                .description("处理需要综合判断的请求")
                .riskLevel(CapabilityRiskLevel.WRITE)
                .timeoutSeconds(240)
                .maxCalls(3)
                .resultTokenBudget(12000)
                .build();
        ApplicationDefinition draft = fallbackDefinitionBuilder()
                .capabilityCatalog(catalog(fallbackCap))
                .build();

        ApplicationDefinitionResolution result = resolve(validator, draft);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("CAPABILITY_TYPE_MANAGED");
    }

    @Test
    void shouldRejectFallbackCodeOutsideCatalog() {
        gateway.addAgent(40L, true, 401L, AgentRole.SUPERVISOR);
        gateway.addVersion(40L, 401L);
        ApplicationCapabilityDefinition fallbackCap = ApplicationCapabilityDefinition.builder()
                .code("other_agent")
                .type(ApplicationCapabilityType.AGENT)
                .targetId(41L)
                .build();
        ApplicationDefinition draft = fallbackDefinitionBuilder()
                .capabilityCatalog(catalog(fallbackCap))
                .build();

        ApplicationDefinitionResolution result = resolve(validator, draft);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("CAPABILITY_TYPE_MANAGED");
    }

    @Test
    void shouldRejectCapabilityDescriptionOverLimit() {
        gateway.addAgent(40L, true, 401L, AgentRole.SUPERVISOR);
        gateway.addVersion(40L, 401L);
        workflows.put(30L, new WorkflowService.WorkflowReference(30L, true, 300L, "退款流程"));
        workflowVersions.put(key(30L, 300L), workflowDefinition());
        ApplicationCapabilityDefinition fallbackCap = ApplicationCapabilityDefinition.builder()
                .code("refund_workflow")
                .type(ApplicationCapabilityType.WORKFLOW)
                .targetId(30L)
                .description("x".repeat(501))
                .build();
        ApplicationDefinition draft = fallbackDefinitionBuilder()
                .capabilityCatalog(catalog(fallbackCap))
                .build();

        ApplicationDefinitionResolution result = resolve(validator, draft);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("CAPABILITY_DESCRIPTION_TOO_LONG");
    }

    @Test
    void shouldRejectExplicitSpecialistCapability() {
        gateway.addAgent(40L, true, 401L, AgentRole.SUPERVISOR);
        gateway.addVersion(40L, 401L);
        ApplicationCapabilityDefinition agentCapability = ApplicationCapabilityDefinition.builder()
                .code("order_agent")
                .type(ApplicationCapabilityType.AGENT)
                .targetId(20L)
                .riskLevel(CapabilityRiskLevel.HIGH_RISK)
                .timeoutSeconds(180)
                .maxCalls(2)
                .resultTokenBudget(6000)
                .build();
        ApplicationDefinition draft = fallbackDefinitionBuilder()
                .capabilityCatalog(catalog(fallbackCapability(null), agentCapability))
                .build();

        ApplicationDefinitionResolution result = resolve(validator, draft);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("CAPABILITY_TYPE_MANAGED");
    }

    @Test
    void shouldRejectToolCapabilityManagedByAgent() {
        gateway.addAgent(40L, true, 401L, AgentRole.SUPERVISOR);
        gateway.addVersion(40L, 401L);
        workflows.put(30L, new WorkflowService.WorkflowReference(30L, true, 300L, "退款流程"));
        workflowVersions.put(key(30L, 300L), workflowDefinition());
        tools.put(50L, ToolReferenceBO.builder()
                .id(50L)
                .name("订单查询")
                .code("order_lookup")
                .description("查询订单当前状态")
                .sourceType(ToolSourceType.LOCAL_FUNCTION)
                .parameterSchema(JSONObject.of("type", "object"))
                .status(EnableStatus.ACTIVE)
                .build());
        CapabilityCatalogSnapshot catalog = catalog(
                        fallbackCapability(null),
                        ApplicationCapabilityDefinition.builder()
                                .code("refund_workflow")
                                .type(ApplicationCapabilityType.WORKFLOW)
                                .targetId(30L)
                                .build(),
                        ApplicationCapabilityDefinition.builder()
                                .code("order_lookup")
                                .type(ApplicationCapabilityType.TOOL)
                                .targetId(50L)
                                .build());
        ApplicationDefinition draft = fallbackDefinitionBuilder()
                .capabilityCatalog(catalog)
                .build();

        ApplicationDefinitionResolution result = resolve(validator, draft);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("CAPABILITY_TYPE_MANAGED");
    }

    @Test
    void shouldAllowSupervisorAsFallback() {
        gateway.addAgent(40L, true, 401L, AgentRole.SUPERVISOR);
        gateway.addVersion(40L, 401L);

        ApplicationDefinitionResolution result = resolve(validator, definition());

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().fallback().cap()).isEqualTo("default_agent");
    }

    @Test
    void shouldRejectNestedSupervisorCapability() {
        gateway.addAgent(40L, true, 401L, AgentRole.SUPERVISOR);
        gateway.addVersion(40L, 401L);
        gateway.addAgent(41L, true, 411L, AgentRole.SUPERVISOR);
        gateway.addVersion(41L, 411L);
        ApplicationCapabilityDefinition nested = ApplicationCapabilityDefinition.builder()
                .code("nested_planner")
                .type(ApplicationCapabilityType.AGENT)
                .targetId(41L)
                .build();
        ApplicationDefinition draft = fallbackDefinitionBuilder()
                .capabilityCatalog(catalog(fallbackCapability(null), nested))
                .build();

        ApplicationDefinitionResolution result = resolve(validator, draft);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("CAPABILITY_TYPE_MANAGED");
    }

    @Test
    void shouldRejectAgentVersionWithLegacySchema() {
        gateway.addAgent(40L, true, 401L, AgentRole.SUPERVISOR);
        gateway.addVersion(40L, 401L, AGENT_SCHEMA_VERSION - 1, AgentRole.SUPERVISOR);

        ApplicationDefinitionResolution result = resolve(validator, definition());

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .containsExactly("COLLABORATION_AGENT_VERSION_NOT_FOUND");
    }

    @Test
    void shouldRejectCapabilityResultExceedingApplicationRuntimeBudget() {
        gateway.addAgent(40L, true, 401L, AgentRole.SUPERVISOR);
        gateway.addVersion(40L, 401L);
        ApplicationCapabilityDefinition fallbackCap = ApplicationCapabilityDefinition.builder()
                .code("fallback_agent")
                .type(ApplicationCapabilityType.AGENT)
                .targetId(40L)
                .timeoutSeconds(200)
                .resultTokenBudget(2000)
                .build();
        ApplicationRuntimePolicy runtimePolicy = ApplicationRuntimePolicy.builder()
                .maxModelCalls(4)
                .maxToolCalls(4)
                .maxDelegations(0)
                .totalTokenBudget(1024)
                .build();
        ApplicationDefinition draft = fallbackDefinitionBuilder()
                .capabilityCatalog(catalog(fallbackCap))
                .runtimePolicy(runtimePolicy)
                .build();

        ApplicationDefinitionResolution result = resolve(validator, draft);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("CAPABILITY_RESULT_BUDGET_EXCEEDS_RUNTIME")
                .doesNotContain("CAPABILITY_TIMEOUT_EXCEEDS_RUNTIME");
    }

    @Test
    void shouldNormalizeAndValidateTokenFinalizationGrace() {
        gateway.addAgent(40L, true, 401L, AgentRole.SUPERVISOR);
        gateway.addVersion(40L, 401L);
        ApplicationRuntimePolicy runtimePolicy = ApplicationRuntimePolicy.defaults();
        runtimePolicy.setTokenGracePercent(31);
        ApplicationDefinition draft = fallbackDefinitionBuilder()
                .runtimePolicy(runtimePolicy)
                .build();

        ApplicationDefinitionResolution result = resolve(validator, draft);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("RUNTIME_TOKEN_GRACE_INVALID");
    }

    @Test
    void shouldInheritIntentTreeEmbeddingModelForMemory() {
        ApplicationDefinitionResolution result = resolveMemoryWithIntentTree(null);

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().memoryConf().getEmbeddingModelInstanceId()).isEqualTo(88L);
        assertThat(result.definition().memoryConf().summary().getPromptVersionId()).isEqualTo(501L);
        assertThat(result.definition().memoryConf().profile().getUpdateStrategy()).isEqualTo(UserProfileUpdateStrategy.MERGE);
        assertThat(result.definition().memoryConf().interactionRecord().getRetentionDays()).isEqualTo(365);
        assertThat(result.definition().memoryConf().summary().getMinTokens()).isEqualTo(4000);
    }

    @Test
    void shouldRejectInvalidMemoryRetentionAndSummaryTokenThresholds() {
        addMemoryReferences();
        MemoryConfig memory = memoryConfig(401L);
        memory.getInteractionRecord().setRetentionDays(3651);
        memory.getSummary().setMinTokens(255);

        ApplicationDefinitionResolution result = resolve(validator, fallbackDefinitionBuilder()
                .memoryConf(memory)
                .build());

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("MEMORY_RETENTION_DAYS_INVALID", "MEMORY_SUMMARY_TOKENS_INVALID");
    }

    @Test
    void shouldRejectMemoryEmbeddingModelDifferentFromIntentTree() {
        ApplicationDefinitionResolution result = resolveMemoryWithIntentTree(401L);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("MEMORY_EMBEDDING_INTENT_MISMATCH");
    }

    @Test
    void shouldAllowIndependentMemoryEmbeddingWithoutIntentTree() {
        addMemoryReferences();
        ApplicationDefinitionResolution result = resolve(validator, fallbackDefinitionBuilder()
                .memoryConf(memoryConfig(401L))
                .build());

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().memoryConf().getEmbeddingModelInstanceId()).isEqualTo(401L);
    }

    @Test
    void shouldAllowProfileAndSummaryWithoutEmbeddingModel() {
        addMemoryReferences();
        MemoryConfig memory = memoryConfig(null);
        memory.setInteractionRecord(InteractionRecordMemoryConfig.disabled());

        ApplicationDefinitionResolution result = resolve(validator, fallbackDefinitionBuilder()
                .memoryConf(memory)
                .build());

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().memoryConf().profile().isEnabled()).isTrue();
        assertThat(result.definition().memoryConf().summary().isEnabled()).isTrue();
        assertThat(result.definition().memoryConf().interactionRecord().isEnabled()).isFalse();
    }

    private ApplicationDefinitionResolution resolveMemoryWithIntentTree(Long memoryEmbeddingInstanceId) {
        IntentNodeDefinition leaf = IntentNodeDefinition.builder()
                .id("chat-node")
                .code("chat")
                .name("闲聊")
                .description("日常对话")
                .nodeType(IntentNodeType.LEAF)
                .enabled(true)
                .build();
        IntentTreeDefinition treeDefinition = new IntentTreeDefinition(
                INTENT_TREE_SCHEMA_VERSION,
                88L,
                intentRoutingPolicy(),
                List.of(leaf));
        ApplicationDraftValidator memoryValidator = new ApplicationDraftValidator(
                gateway,
                intentGateway(intentTreeId -> Optional.of(new ApplicationIntentTreeReferenceGateway.IntentTreeReference(
                        intentTreeId, true, 200L, treeDefinition))),
                workflowService,
                modelInstanceService,
                promptService,
                domainRepo);
        addMemoryReferences();
        models.put(88L, modelReference(88L, ModelType.EMBEDDING));
        ApplicationIntentBinding binding = ApplicationIntentBinding.builder()
                .enabled(true)
                .intentTreeId(2L)
                .mappings(List.of(ApplicationIntentMapping.builder()
                        .intentCode("chat")
                        .targetType(IntentExecutionTargetType.FIXED_RESPONSE)
                        .fixedResponse("你好，请问有什么可以帮你？")
                        .build()))
                .build();
        return resolve(memoryValidator, fallbackDefinitionBuilder()
                .intentRouting(binding)
                .memoryConf(memoryConfig(memoryEmbeddingInstanceId))
                .build());
    }

    private void addMemoryReferences() {
        models.put(401L, modelReference(401L, ModelType.EMBEDDING));
        models.put(402L, modelReference(402L, ModelType.CHAT));
        prompts.put(501L, new PromptService.PromptVersionReference(501L, PromptRole.SYSTEM, true, List.of()));
        prompts.put(502L, new PromptService.PromptVersionReference(502L, PromptRole.SYSTEM, true, List.of()));
        prompts.put(503L, new PromptService.PromptVersionReference(503L, PromptRole.SYSTEM, true, List.of()));
    }

    private static MemoryConfig memoryConfig(Long embeddingInstanceId) {
        return MemoryConfig.builder()
                .chatModelInstanceId(402L)
                .embeddingModelInstanceId(embeddingInstanceId)
                .profile(UserProfileMemoryConfig.builder()
                        .enabled(true)
                        .promptVersionId(502L)
                        .build())
                .interactionRecord(InteractionRecordMemoryConfig.builder()
                        .enabled(true)
                        .promptVersionId(503L)
                        .topK(5)
                        .build())
                .summary(ConversationSummaryMemoryConfig.builder()
                        .enabled(true)
                        .promptVersionId(501L)
                        .minRounds(8)
                        .build())
                .build();
    }

    @Test
    void shouldValidateAndFreezeContextualizationPolicy() {
        gateway.addAgent(10L, true, 101L);
        gateway.addVersion(10L, 101L);
        models.put(601L, modelReference(601L, ModelType.CHAT));
        prompts.put(701L, new PromptService.PromptVersionReference(
                701L, PromptRole.SYSTEM, true, contextualizationVariables()));
        ContextualizationPolicy contextualization = ContextualizationPolicy.builder()
                .enabled(true)
                .chatModelInstanceId(601L)
                .promptVersionId(701L)
                .fallbackToOriginal(false)
                .maxOutputTokens(300)
                .maxOutputCharacters(1200)
                .timeoutSeconds(20)
                .build();
        ApplicationDefinition draft = fallbackDefinitionBuilder()
                .contextPolicy(ApplicationContextPolicy.builder()
                        .contextualization(contextualization)
                        .budget(ContextBudgetPolicy.defaults())
                        .build())
                .build();

        ApplicationDefinitionResolution result = resolve(validator, draft);

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().schemaVersion()).isEqualTo(APPLICATION_SCHEMA_VERSION);
        assertThat(result.definition().contextPolicy().contextualization().getChatModelInstanceId()).isEqualTo(601L);
        assertThat(result.definition().contextPolicy().contextualization().getPromptVersionId()).isEqualTo(701L);
        assertThat(result.definition().contextPolicy().budget()).isEqualTo(ContextBudgetPolicy.defaults());
    }

    @Test
    void shouldRejectUnavailableContextualizationReferencesAndInvalidBudget() {
        gateway.addAgent(10L, true, 101L);
        gateway.addVersion(10L, 101L);
        ContextBudgetPolicy invalidBudget = ContextBudgetPolicy.defaults();
        invalidBudget.setMaxInputTokens(0);
        ApplicationDefinition draft = fallbackDefinitionBuilder()
                .contextPolicy(ApplicationContextPolicy.builder()
                        .contextualization(ContextualizationPolicy.builder().enabled(true).build())
                        .preset(ContextBudgetPreset.CUSTOM)
                        .budget(invalidBudget)
                        .build())
                .build();

        ApplicationDefinitionResolution result = resolve(validator, draft);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("CONTEXTUALIZATION_MODEL_REQUIRED", "CONTEXTUALIZATION_PROMPT_REQUIRED", "CONTEXT_BUDGET_INVALID");
    }

    @Test
    void shouldRejectRemovedVariableInContextualizationPrompt() {
        gateway.addAgent(10L, true, 101L);
        gateway.addVersion(10L, 101L);
        models.put(601L, modelReference(601L, ModelType.CHAT));
        List<PromptService.PromptVariableReference> variables = List.of(
                new PromptService.PromptVariableReference("query.rewrite_history", PromptVariableType.MESSAGE_LIST));
        prompts.put(701L, new PromptService.PromptVersionReference(701L, PromptRole.SYSTEM, true, variables));
        ApplicationDefinition draft = fallbackDefinitionBuilder()
                .contextPolicy(ApplicationContextPolicy.builder()
                        .contextualization(ContextualizationPolicy.builder()
                                .enabled(true)
                                .chatModelInstanceId(601L)
                                .promptVersionId(701L)
                                .build())
                        .budget(ContextBudgetPolicy.defaults())
                        .build())
                .build();

        ApplicationDefinitionResolution result = resolve(validator, draft);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("CONTEXTUALIZATION_PROMPT_VARIABLE_UNSUPPORTED");
    }

    @Test
    void shouldRejectMissingRequiredContextualizationVariables() {
        gateway.addAgent(10L, true, 101L);
        gateway.addVersion(10L, 101L);
        models.put(601L, modelReference(601L, ModelType.CHAT));
        prompts.put(701L, new PromptService.PromptVersionReference(701L, PromptRole.SYSTEM, true, List.of()));
        ApplicationDefinition draft = fallbackDefinitionBuilder()
                .contextPolicy(ApplicationContextPolicy.builder()
                        .contextualization(ContextualizationPolicy.builder()
                                .enabled(true)
                                .chatModelInstanceId(601L)
                                .promptVersionId(701L)
                                .build())
                        .budget(ContextBudgetPolicy.defaults())
                        .build())
                .build();

        ApplicationDefinitionResolution result = resolve(validator, draft);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::message)
                .contains("问题重写Prompt必须声明变量：user.query", "问题重写Prompt必须声明变量：query.contextualization_history");
    }

    private static List<PromptService.PromptVariableReference> contextualizationVariables() {
        return List.of(
                new PromptService.PromptVariableReference("user.query", PromptVariableType.STRING),
                new PromptService.PromptVariableReference("query.contextualization_history", PromptVariableType.MESSAGE_LIST));
    }

    private static ModelInstanceReferenceBO modelReference(Long id, ModelType modelType) {
        return ModelInstanceReferenceBO.builder()
                .id(id)
                .modelType(modelType)
                .status(EnableStatus.ACTIVE)
                .build();
    }

    private static IntentRoutingPolicy intentRoutingPolicy() {
        return IntentRoutingPolicy.builder()
                .topK(5)
                .recallThreshold(new BigDecimal("0.65"))
                .chatModelInstanceId(89L)
                .decisionPromptVersionId(90L)
                .maxOutputTokens(512)
                .timeoutSeconds(30)
                .maxRepeatAttempts(1)
                .decisionFailureAction(IntentDecisionFailureAction.DEFER)
                .build();
    }

    private static ApplicationDefinition definition() {
        return fallbackDefinitionBuilder().build();
    }

    private static ApplicationDefinition.ApplicationDefinitionBuilder fallbackDefinitionBuilder() {
        return ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .collaboration(new AgentCollaborationDefinition(
                        new ApplicationAgentReference(40L, null, null, null),
                        List.of(),
                        List.of(),
                        List.of()))
                .capabilityCatalog(catalog());
    }

    private static CapabilityCatalogSnapshot catalog(ApplicationCapabilityDefinition... capabilities) {
        return CapabilityCatalogSnapshot.builder().capabilities(List.of(capabilities)).build();
    }

    private static ApplicationCapabilityDefinition fallbackCapability(Long versionId) {
        return ApplicationCapabilityDefinition.builder()
                .code("fallback_agent")
                .type(ApplicationCapabilityType.AGENT)
                .targetId(40L)
                .targetVersionId(versionId)
                .build();
    }

    private static ApplicationCapabilityDefinition resolvedCapability(
            ApplicationDefinition definition, String capabilityCode) {
        return definition.capabilityCatalog().capabilities().stream()
                .filter(capability -> capabilityCode.equals(capability.getCode()))
                .findFirst()
                .orElseThrow();
    }

    private static WorkflowDefinition workflowDefinition() {
        return new WorkflowDefinition(2, "refund", List.of(), List.of());
    }

    private WorkflowService workflowService() {
        return (WorkflowService) Proxy.newProxyInstance(
                WorkflowService.class.getClassLoader(),
                new Class<?>[]{WorkflowService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findReference" -> Optional.ofNullable(workflows.get((Long) args[0]));
                    case "findVersion" -> Optional.ofNullable(workflowVersions.get(key((Long) args[0], (Long) args[1])));
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private ModelInstanceService modelInstanceService() {
        return (ModelInstanceService) Proxy.newProxyInstance(
                ModelInstanceService.class.getClassLoader(),
                new Class<?>[]{ModelInstanceService.class},
                (proxy, method, args) -> {
                    if ("findReference".equals(method.getName())) {
                        return Optional.ofNullable(models.get((Long) args[0]));
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private PromptService promptService() {
        return (PromptService) Proxy.newProxyInstance(
                PromptService.class.getClassLoader(),
                new Class<?>[]{PromptService.class},
                (proxy, method, args) -> {
                    if ("findPublishedVersion".equals(method.getName())) {
                        return Optional.ofNullable(prompts.get((Long) args[0]));
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private ToolService toolService() {
        return (ToolService) Proxy.newProxyInstance(
                ToolService.class.getClassLoader(),
                new Class<?>[]{ToolService.class},
                (proxy, method, args) -> {
                    if ("findReference".equals(method.getName())) {
                        return Optional.ofNullable(tools.get((Long) args[0]));
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static String key(Long resourceId, Long versionId) {
        return resourceId + ":" + versionId;
    }

    private static ApplicationDefinitionResolution resolve(ApplicationDraftValidator validator, ApplicationDefinition definition) {
        return validator.resolveForPublish(definition, 1L);
    }

    private static ApplicationIntentTreeReferenceGateway intentGateway(
            Function<Long, Optional<ApplicationIntentTreeReferenceGateway.IntentTreeReference>> finder) {
        return new ApplicationIntentTreeReferenceGateway() {
            @Override
            public Optional<IntentTreeReference> findPublishedTree(Long intentTreeId) {
                return finder.apply(intentTreeId);
            }

            @Override
            public Optional<Long> findDomainId(Long intentTreeId) {
                return Optional.of(1L);
            }
        };
    }

    private static DomainRepo domainRepo() {
        DomainRepo repo = mock(DomainRepo.class);
        DomainEntity domain = new DomainEntity();
        domain.setId(1L);
        domain.setRootId(1L);
        domain.setDepth(1);
        domain.setStatus(EnableStatus.ACTIVE);
        when(repo.findById(anyLong())).thenReturn(Optional.of(domain));
        when(repo.findActiveByRootId(anyLong())).thenReturn(List.of(domain));
        return repo;
    }

    private static final class StubAgentReferenceGateway
            implements com.lingxi.workbench.admin.agent.service.AgentReferenceService {
        private final Map<Long, AgentReference> agents = new HashMap<>();
        private final Map<String, AgentVersionReference> versions = new HashMap<>();

        private StubAgentReferenceGateway() {
            addAgent(40L, true, 401L, AgentRole.SUPERVISOR);
            addVersion(40L, 401L);
            addAgent(20L, true, 201L, AgentRole.SPECIALIST);
            addVersion(20L, 201L);
        }

        void addAgent(Long id, boolean active, Long publishedVersionId) {
            addAgent(id, active, publishedVersionId, id == 40L ? AgentRole.SUPERVISOR : AgentRole.SPECIALIST);
        }

        void addAgent(Long id, boolean active, Long publishedVersionId, AgentRole role) {
            agents.put(id, new AgentReference(
                    new AgentIdentity(id, "Agent-" + id, "Agent职责-" + id, role), active, publishedVersionId, 1L));
        }

        void addVersion(Long agentId, Long versionId) {
            AgentRole role = agents.get(agentId).identity().role();
            addVersion(agentId, versionId, AGENT_SCHEMA_VERSION, role);
        }

        void addVersion(Long agentId, Long versionId, int schemaVersion, AgentRole role) {
            versions.put(key(agentId, versionId), new AgentVersionReference(versionId, schemaVersion, role));
        }

        @Override
        public Optional<AgentReference> findAgent(Long agentId) {
            return Optional.ofNullable(agents.get(agentId));
        }

        @Override
        public Optional<AgentVersionReference> findVersion(Long agentId, Long versionId) {
            return Optional.ofNullable(versions.get(key(agentId, versionId)));
        }

        @Override
        public List<AgentReference> listPublishedSpecialists(java.util.Set<Long> domainIds) {
            return agents.values().stream()
                    .filter(AgentReference::active)
                    .filter(agent -> agent.identity().role() == AgentRole.SPECIALIST)
                    .filter(agent -> agent.publishedVersionId() != null)
                    .filter(agent -> domainIds.contains(agent.domainId()))
                    .toList();
        }

    }
}

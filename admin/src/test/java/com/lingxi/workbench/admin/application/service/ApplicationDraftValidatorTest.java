package com.lingxi.workbench.admin.application.service;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentType;
import com.lingxi.workbench.admin.application.model.vo.ApplicationValidationIssueVO;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
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
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.CapabilityRiskLevel;
import com.lingxi.workbench.workflow.model.ApplicationContextPolicy;
import com.lingxi.workbench.workflow.model.AgentCollaborationDefinition;
import com.lingxi.workbench.workflow.model.AgentCollaborationLimits;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.workflow.model.ConversationSummaryMemoryConfig;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import com.lingxi.workbench.workflow.model.ContextualizationPolicy;
import com.lingxi.workbench.workflow.model.InteractionRecordMemoryConfig;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import com.lingxi.workbench.workflow.model.PlannerPolicy;
import com.lingxi.workbench.workflow.model.UserProfileMemoryConfig;
import com.lingxi.workbench.workflow.model.UserProfileUpdateStrategy;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import com.lingxi.workbench.workflow.model.intent.IntentNodeDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentNodeType;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

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
    private final ApplicationDraftValidator validator =
            new ApplicationDraftValidator(
                    gateway,
                    intentTreeId -> Optional.empty(),
                    workflowService,
                    modelInstanceService,
                    promptService,
                    toolService);

    @Test
    void shouldLockCurrentPublishedDefaultAgentVersion() {
        gateway.addAgent(40L, true, 402L);
        gateway.addVersion(40L, 402L);

        ApplicationDefinitionResolution result = validator.resolveForPublish(definition(null));

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().defaultAgent().agentId()).isEqualTo(40L);
        assertThat(result.definition().defaultAgent().agentVersionId()).isEqualTo(402L);
    }

    @Test
    void shouldKeepExplicitPublishedDefaultAgentVersion() {
        gateway.addAgent(40L, true, 402L);
        gateway.addVersion(40L, 401L);

        ApplicationDefinitionResolution result = validator.resolveForPublish(definition(401L));

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().defaultAgent().agentVersionId()).isEqualTo(401L);
    }

    @Test
    void shouldRejectDisabledDefaultAgent() {
        gateway.addAgent(40L, false, 401L);

        ApplicationDefinitionResolution result = validator.resolveForPublish(definition(null));

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("DEFAULT_AGENT_UNAVAILABLE", "EXECUTION_ENTRY_REQUIRED");
    }

    @Test
    void shouldRejectUnavailableDefaultAgentVersion() {
        gateway.addAgent(40L, true, 401L);

        ApplicationDefinitionResolution result = validator.resolveForPublish(definition(999L));

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("DEFAULT_AGENT_VERSION_UNAVAILABLE", "EXECUTION_ENTRY_REQUIRED");
    }

    @Test
    void shouldResolveSupervisorAndMemberPublishedVersions() {
        gateway.addAgent(10L, true, 101L, AgentType.SUPERVISOR);
        gateway.addVersion(10L, 101L);
        gateway.addAgent(20L, true, 201L);
        gateway.addVersion(20L, 201L);
        AgentCollaborationDefinition collaboration = new AgentCollaborationDefinition(true,
                new ApplicationAgentReference(10L, null, null, null),
                List.of(new ApplicationAgentReference(20L, null, null, null)), new AgentCollaborationLimits(1));

        ApplicationDefinitionResolution result = validator.resolveForPublish(ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION).collaboration(collaboration)
                .defaultAgent(new ApplicationAgentReference(40L, null, null, null)).build());

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().collaboration().supervisor().agentVersionId()).isEqualTo(101L);
        assertThat(result.definition().collaboration().members().getFirst().agentVersionId()).isEqualTo(201L);
        assertThat(result.definition().collaboration().members().getFirst().name()).isEqualTo("Agent-20");
    }

    @Test
    void shouldRejectSupervisorDuplicatedAsMember() {
        gateway.addAgent(10L, true, 101L, AgentType.SUPERVISOR);
        gateway.addVersion(10L, 101L);
        AgentCollaborationDefinition collaboration = new AgentCollaborationDefinition(true,
                new ApplicationAgentReference(10L, null, null, null),
                List.of(new ApplicationAgentReference(10L, null, null, null)), new AgentCollaborationLimits(1));

        ApplicationDefinitionResolution result = validator.resolveForPublish(ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION).collaboration(collaboration)
                .defaultAgent(new ApplicationAgentReference(40L, null, null, null)).build());

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(issue -> issue.code()).contains("SUPERVISOR_AS_MEMBER");
    }

    @Test
    void shouldRejectReactAgentUsedAsSupervisor() {
        gateway.addAgent(10L, true, 101L, AgentType.REACT);
        gateway.addVersion(10L, 101L);
        gateway.addAgent(20L, true, 201L, AgentType.REACT);
        gateway.addVersion(20L, 201L);
        AgentCollaborationDefinition collaboration = new AgentCollaborationDefinition(true,
                new ApplicationAgentReference(10L, null, null, null),
                List.of(new ApplicationAgentReference(20L, null, null, null)), new AgentCollaborationLimits(1));

        ApplicationDefinitionResolution result = validator.resolveForPublish(ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION).collaboration(collaboration)
                .defaultAgent(new ApplicationAgentReference(40L, null, null, null)).build());

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(issue -> issue.code()).contains("COLLABORATION_AGENT_TYPE_INVALID");
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
                1,
                88L,
                new IntentRoutingPolicy(5, new BigDecimal("0.65"), new BigDecimal("0.08")),
                List.of(leaf, exchangeLeaf));
        ApplicationDraftValidator intentValidator = new ApplicationDraftValidator(
                gateway,
                intentTreeId -> Optional.of(new ApplicationIntentTreeReferenceGateway.IntentTreeReference(
                        intentTreeId, true, 200L, treeDefinition)),
                workflowService,
                modelInstanceService,
                promptService,
                toolService);
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

        ApplicationDefinitionResolution result = intentValidator.resolveForPublish(
                ApplicationDefinition.builder()
                        .schemaVersion(APPLICATION_SCHEMA_VERSION)
                        .defaultAgent(new ApplicationAgentReference(40L, null, null, null))
                        .capabilityCatalog(CapabilityCatalogSnapshot.builder()
                                .capabilities(List.of(ApplicationCapabilityDefinition.builder()
                                        .code("refund_workflow")
                                        .type(ApplicationCapabilityType.WORKFLOW)
                                        .targetId(30L)
                                        .build()))
                                .build())
                        .optionalFastRoutes(binding)
                        .build());

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().optionalFastRoutes().getIntentTreeVersionId()).isEqualTo(200L);
        assertThat(result.definition().optionalFastRoutes().mappings()).singleElement()
                .satisfies(mapping -> {
                    assertThat(mapping.getCapabilityCode()).isEqualTo("refund_workflow");
                    assertThat(mapping.getTargetVersionId()).isEqualTo(300L);
                });
    }

    @Test
    void shouldRejectApplicationWithoutExecutionEntry() {
        ApplicationDefinitionResolution result = validator.resolveForPublish(
                ApplicationDefinition.builder().schemaVersion(APPLICATION_SCHEMA_VERSION).build());

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(issue -> issue.code()).containsExactly("EXECUTION_ENTRY_REQUIRED");
    }

    @Test
    void shouldRejectLegacyApplicationSchema() {
        ApplicationDefinitionResolution result = validator.resolveForPublish(
                ApplicationDefinition.builder().schemaVersion(APPLICATION_SCHEMA_VERSION - 1).build());

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .containsExactly("SCHEMA_VERSION_UNSUPPORTED");
    }

    @Test
    void shouldPublishDefaultReactAgentWithoutIntentTree() {
        gateway.addAgent(40L, true, 401L, AgentType.REACT);
        gateway.addVersion(40L, 401L);
        ApplicationDefinition draft = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(new ApplicationAgentReference(40L, null, null, null))
                .build();

        ApplicationDefinitionResolution result = validator.resolveForPublish(draft);

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().schemaVersion()).isEqualTo(7);
        assertThat(result.definition().optionalFastRoutes()).isNull();
        assertThat(result.definition().defaultAgent().agentVersionId()).isEqualTo(401L);
        assertThat(result.definition().capabilityCatalog().capabilities())
                .singleElement()
                .satisfies(capability -> {
                    assertThat(capability.getCode()).isEqualTo("default_agent");
                    assertThat(capability.getTargetVersionId()).isEqualTo(401L);
                });
    }

    @Test
    void shouldPreserveDefaultAgentCapabilityPolicy() {
        gateway.addAgent(40L, true, 401L, AgentType.REACT);
        gateway.addVersion(40L, 401L);
        ApplicationCapabilityDefinition defaultCapability = ApplicationCapabilityDefinition.builder()
                .code("default_agent")
                .type(ApplicationCapabilityType.REACT_AGENT)
                .targetId(40L)
                .description("处理需要综合判断的请求")
                .riskLevel(CapabilityRiskLevel.WRITE)
                .timeoutSeconds(240)
                .maxCalls(3)
                .resultTokenBudget(12000)
                .build();
        ApplicationDefinition draft = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(new ApplicationAgentReference(40L, null, null, null))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder().capabilities(List.of(defaultCapability)).build())
                .build();

        ApplicationDefinitionResolution result = validator.resolveForPublish(draft);

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().capabilityCatalog().capabilities()).singleElement().satisfies(capability -> {
            assertThat(capability.getDescription()).isEqualTo("处理需要综合判断的请求");
            assertThat(capability.getRiskLevel()).isEqualTo(CapabilityRiskLevel.WRITE);
            assertThat(capability.getTimeoutSeconds()).isEqualTo(240);
            assertThat(capability.getMaxCalls()).isEqualTo(3);
            assertThat(capability.getResultTokenBudget()).isEqualTo(12000);
        });
    }

    @Test
    void shouldRejectDefaultCapabilityTargetDifferentFromEntry() {
        gateway.addAgent(40L, true, 401L, AgentType.REACT);
        gateway.addVersion(40L, 401L);
        ApplicationCapabilityDefinition defaultCapability = ApplicationCapabilityDefinition.builder()
                .code("default_agent")
                .type(ApplicationCapabilityType.REACT_AGENT)
                .targetId(41L)
                .build();
        ApplicationDefinition draft = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(new ApplicationAgentReference(40L, null, null, null))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder().capabilities(List.of(defaultCapability)).build())
                .build();

        ApplicationDefinitionResolution result = validator.resolveForPublish(draft);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("DEFAULT_AGENT_CAPABILITY_TARGET_INVALID");
    }

    @Test
    void shouldRejectCapabilityDescriptionOverLimit() {
        gateway.addAgent(40L, true, 401L, AgentType.REACT);
        gateway.addVersion(40L, 401L);
        ApplicationCapabilityDefinition defaultCapability = ApplicationCapabilityDefinition.builder()
                .code("default_agent")
                .type(ApplicationCapabilityType.REACT_AGENT)
                .targetId(40L)
                .description("x".repeat(501))
                .build();
        ApplicationDefinition draft = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(new ApplicationAgentReference(40L, null, null, null))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder().capabilities(List.of(defaultCapability)).build())
                .build();

        ApplicationDefinitionResolution result = validator.resolveForPublish(draft);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("CAPABILITY_DESCRIPTION_TOO_LONG");
    }

    @Test
    void shouldPreserveReactAgentCapabilityPolicy() {
        gateway.addAgent(40L, true, 401L, AgentType.REACT);
        gateway.addVersion(40L, 401L);
        gateway.addAgent(41L, true, 411L, AgentType.REACT);
        gateway.addVersion(41L, 411L);
        ApplicationCapabilityDefinition agentCapability = ApplicationCapabilityDefinition.builder()
                .code("order_agent")
                .type(ApplicationCapabilityType.REACT_AGENT)
                .targetId(41L)
                .riskLevel(CapabilityRiskLevel.HIGH_RISK)
                .timeoutSeconds(180)
                .maxCalls(2)
                .resultTokenBudget(6000)
                .build();
        ApplicationDefinition draft = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(new ApplicationAgentReference(40L, null, null, null))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder().capabilities(List.of(agentCapability)).build())
                .build();

        ApplicationDefinitionResolution result = validator.resolveForPublish(draft);

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().capabilityCatalog().capabilities())
                .filteredOn(capability -> "order_agent".equals(capability.getCode()))
                .singleElement()
                .satisfies(capability -> {
                    assertThat(capability.getRiskLevel()).isEqualTo(CapabilityRiskLevel.HIGH_RISK);
                    assertThat(capability.getTimeoutSeconds()).isEqualTo(180);
                    assertThat(capability.getMaxCalls()).isEqualTo(2);
                    assertThat(capability.getResultTokenBudget()).isEqualTo(6000);
                });
    }

    @Test
    void shouldFreezeWorkflowAndToolCapabilities() {
        gateway.addAgent(40L, true, 401L, AgentType.REACT);
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
        CapabilityCatalogSnapshot catalog = CapabilityCatalogSnapshot.builder()
                .capabilities(List.of(
                        ApplicationCapabilityDefinition.builder()
                                .code("refund_workflow")
                                .type(ApplicationCapabilityType.WORKFLOW)
                                .targetId(30L)
                                .build(),
                        ApplicationCapabilityDefinition.builder()
                                .code("order_lookup")
                                .type(ApplicationCapabilityType.TOOL)
                                .targetId(50L)
                                .build()))
                .build();
        ApplicationDefinition draft = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(new ApplicationAgentReference(40L, null, null, null))
                .capabilityCatalog(catalog)
                .build();

        ApplicationDefinitionResolution result = validator.resolveForPublish(draft);

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().capabilityCatalog().capabilities()).hasSize(3);
        assertThat(result.definition().capabilityCatalog().capabilities())
                .filteredOn(capability -> capability.getCode().equals("refund_workflow"))
                .singleElement()
                .satisfies(capability -> assertThat(capability.getTargetVersionId()).isEqualTo(300L));
        assertThat(result.definition().capabilityCatalog().capabilities())
                .filteredOn(capability -> capability.getCode().equals("order_lookup"))
                .singleElement()
                .satisfies(capability -> {
                    assertThat(capability.getImplementationType()).isEqualTo("LOCAL_FUNCTION");
                    assertThat(capability.getConfigurationChecksum()).hasSize(64);
                });
    }

    @Test
    void shouldValidateAndFreezePlannerPolicy() {
        gateway.addAgent(40L, true, 401L, AgentType.REACT);
        gateway.addVersion(40L, 401L);
        models.put(601L, new ModelInstanceReferenceBO(601L, ModelType.CHAT, EnableStatus.ACTIVE));
        prompts.put(701L, new PromptService.PromptVersionReference(701L, PromptRole.SYSTEM, true, List.of()));
        PlannerPolicy planner = PlannerPolicy.builder()
                .enabled(true)
                .chatModelInstanceId(601L)
                .promptVersionId(701L)
                .maxSteps(6)
                .maxReplans(1)
                .build();
        ApplicationDefinition draft = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(new ApplicationAgentReference(40L, null, null, null))
                .plannerPolicy(planner)
                .runtimePolicy(ApplicationRuntimePolicy.defaults())
                .build();

        ApplicationDefinitionResolution result = validator.resolveForPublish(draft);

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().plannerPolicy()).isEqualTo(planner);
    }

    @Test
    void shouldRejectInvalidPlannerReferencesAndPlanningBounds() {
        gateway.addAgent(40L, true, 401L, AgentType.REACT);
        gateway.addVersion(40L, 401L);
        PlannerPolicy planner = PlannerPolicy.builder()
                .enabled(true)
                .maxSteps(21)
                .maxReplans(4)
                .build();
        ApplicationDefinition draft = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(new ApplicationAgentReference(40L, null, null, null))
                .plannerPolicy(planner)
                .runtimePolicy(ApplicationRuntimePolicy.defaults())
                .build();

        ApplicationDefinitionResolution result = validator.resolveForPublish(draft);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("PLANNER_MODEL_REQUIRED", "PLANNER_PROMPT_REQUIRED", "PLANNER_STEPS_INVALID",
                        "PLANNER_REPLANS_INVALID");
    }

    @Test
    void shouldRequireSharedModelBudgetForPlannerAndTargetExecution() {
        gateway.addAgent(40L, true, 401L, AgentType.REACT);
        gateway.addVersion(40L, 401L);
        models.put(601L, new ModelInstanceReferenceBO(601L, ModelType.CHAT, EnableStatus.ACTIVE));
        prompts.put(701L, new PromptService.PromptVersionReference(701L, PromptRole.SYSTEM, true, List.of()));
        PlannerPolicy planner = PlannerPolicy.builder().enabled(true).chatModelInstanceId(601L)
                .promptVersionId(701L).maxSteps(6).maxReplans(1).build();
        ApplicationRuntimePolicy runtime = ApplicationRuntimePolicy.builder()
                .maxModelCalls(1).maxToolCalls(4).maxDelegations(0)
                .totalTokenBudget(64000).timeoutSeconds(300).build();

        ApplicationDefinitionResolution result = validator.resolveForPublish(ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(new ApplicationAgentReference(40L, null, null, null))
                .plannerPolicy(planner)
                .runtimePolicy(runtime)
                .build());

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("RUNTIME_MODEL_CALLS_INSUFFICIENT");
    }

    @Test
    void shouldRejectCapabilityBoundaryExceedingApplicationRuntimeBudget() {
        gateway.addAgent(40L, true, 401L, AgentType.REACT);
        gateway.addVersion(40L, 401L);
        ApplicationCapabilityDefinition defaultCapability = ApplicationCapabilityDefinition.builder()
                .code("default_agent")
                .type(ApplicationCapabilityType.REACT_AGENT)
                .targetId(40L)
                .timeoutSeconds(200)
                .resultTokenBudget(2000)
                .build();
        ApplicationRuntimePolicy runtimePolicy = ApplicationRuntimePolicy.builder()
                .maxModelCalls(4)
                .maxToolCalls(4)
                .maxDelegations(0)
                .totalTokenBudget(1024)
                .timeoutSeconds(100)
                .build();
        ApplicationDefinition draft = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(new ApplicationAgentReference(40L, null, null, null))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder()
                        .capabilities(List.of(defaultCapability))
                        .build())
                .runtimePolicy(runtimePolicy)
                .build();

        ApplicationDefinitionResolution result = validator.resolveForPublish(draft);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("CAPABILITY_TIMEOUT_EXCEEDS_RUNTIME", "CAPABILITY_RESULT_BUDGET_EXCEEDS_RUNTIME");
    }

    @Test
    void shouldNormalizeAndValidateTokenFinalizationGrace() {
        gateway.addAgent(40L, true, 401L, AgentType.REACT);
        gateway.addVersion(40L, 401L);
        ApplicationRuntimePolicy runtimePolicy = ApplicationRuntimePolicy.defaults();
        runtimePolicy.setTokenGracePercent(31);
        ApplicationDefinition draft = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(new ApplicationAgentReference(40L, null, null, null))
                .runtimePolicy(runtimePolicy)
                .build();

        ApplicationDefinitionResolution result = validator.resolveForPublish(draft);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("RUNTIME_TOKEN_GRACE_INVALID");
    }

    @Test
    void shouldInheritIntentTreeEmbeddingModelForMemory() {
        ApplicationDefinitionResolution result = resolveMemoryWithIntentTree(null);

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().memoryConf().interactionRecord().getEmbeddingModelInstanceId()).isEqualTo(88L);
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

        ApplicationDefinitionResolution result = validator.resolveForPublish(ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(new ApplicationAgentReference(40L, null, null, null))
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
        ApplicationDefinitionResolution result = validator.resolveForPublish(ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(new ApplicationAgentReference(40L, null, null, null))
                .memoryConf(memoryConfig(401L))
                .build());

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().memoryConf().interactionRecord().getEmbeddingModelInstanceId()).isEqualTo(401L);
    }

    @Test
    void shouldAllowProfileAndSummaryWithoutEmbeddingModel() {
        addMemoryReferences();
        MemoryConfig memory = memoryConfig(null);
        memory.setInteractionRecord(InteractionRecordMemoryConfig.disabled());

        ApplicationDefinitionResolution result = validator.resolveForPublish(ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(new ApplicationAgentReference(40L, null, null, null))
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
                1,
                88L,
                new IntentRoutingPolicy(5, new BigDecimal("0.65"), new BigDecimal("0.08")),
                List.of(leaf));
        ApplicationDraftValidator memoryValidator = new ApplicationDraftValidator(
                gateway,
                intentTreeId -> Optional.of(new ApplicationIntentTreeReferenceGateway.IntentTreeReference(
                        intentTreeId, true, 200L, treeDefinition)),
                workflowService,
                modelInstanceService,
                promptService,
                toolService);
        addMemoryReferences();
        models.put(88L, new ModelInstanceReferenceBO(88L, ModelType.EMBEDDING, EnableStatus.ACTIVE));
        ApplicationIntentBinding binding = ApplicationIntentBinding.builder()
                .enabled(true)
                .intentTreeId(2L)
                .mappings(List.of(ApplicationIntentMapping.builder()
                        .intentCode("chat")
                        .targetType(IntentExecutionTargetType.FIXED_RESPONSE)
                        .fixedResponse("你好，请问有什么可以帮你？")
                        .build()))
                .build();
        return memoryValidator.resolveForPublish(ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(new ApplicationAgentReference(40L, null, null, null))
                .optionalFastRoutes(binding)
                .memoryConf(memoryConfig(memoryEmbeddingInstanceId))
                .build());
    }

    private void addMemoryReferences() {
        models.put(401L, new ModelInstanceReferenceBO(401L, ModelType.EMBEDDING, EnableStatus.ACTIVE));
        models.put(402L, new ModelInstanceReferenceBO(402L, ModelType.CHAT, EnableStatus.ACTIVE));
        prompts.put(501L, new PromptService.PromptVersionReference(501L, PromptRole.SYSTEM, true, List.of()));
        prompts.put(502L, new PromptService.PromptVersionReference(502L, PromptRole.SYSTEM, true, List.of()));
        prompts.put(503L, new PromptService.PromptVersionReference(503L, PromptRole.SYSTEM, true, List.of()));
    }

    private static MemoryConfig memoryConfig(Long embeddingInstanceId) {
        return MemoryConfig.builder()
                .profile(UserProfileMemoryConfig.builder()
                        .enabled(true)
                        .chatModelInstanceId(402L)
                        .promptVersionId(502L)
                        .build())
                .interactionRecord(InteractionRecordMemoryConfig.builder()
                        .enabled(true)
                        .embeddingModelInstanceId(embeddingInstanceId)
                        .chatModelInstanceId(402L)
                        .promptVersionId(503L)
                        .topK(5)
                        .build())
                .summary(ConversationSummaryMemoryConfig.builder()
                        .enabled(true)
                        .chatModelInstanceId(402L)
                        .promptVersionId(501L)
                        .minRounds(8)
                        .build())
                .build();
    }

    @Test
    void shouldValidateAndFreezeContextualizationPolicy() {
        gateway.addAgent(10L, true, 101L);
        gateway.addVersion(10L, 101L);
        models.put(601L, new ModelInstanceReferenceBO(601L, ModelType.CHAT, EnableStatus.ACTIVE));
        prompts.put(701L, new PromptService.PromptVersionReference(701L, PromptRole.SYSTEM, true, List.of()));
        ContextualizationPolicy contextualization = ContextualizationPolicy.builder()
                .enabled(true)
                .chatModelInstanceId(601L)
                .promptVersionId(701L)
                .fallbackToOriginal(false)
                .maxOutputTokens(300)
                .maxOutputCharacters(1200)
                .timeoutSeconds(20)
                .build();
        ApplicationDefinition draft = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(new ApplicationAgentReference(40L, null, null, null))
                .contextPolicy(ApplicationContextPolicy.builder()
                        .contextualization(contextualization)
                        .budget(ContextBudgetPolicy.defaults())
                        .build())
                .build();

        ApplicationDefinitionResolution result = validator.resolveForPublish(draft);

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().schemaVersion()).isEqualTo(7);
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
        ApplicationDefinition draft = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(new ApplicationAgentReference(40L, null, null, null))
                .contextPolicy(ApplicationContextPolicy.builder()
                        .contextualization(ContextualizationPolicy.builder().enabled(true).build())
                        .budget(invalidBudget)
                        .build())
                .build();

        ApplicationDefinitionResolution result = validator.resolveForPublish(draft);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("CONTEXTUALIZATION_MODEL_REQUIRED", "CONTEXTUALIZATION_PROMPT_REQUIRED", "CONTEXT_BUDGET_INVALID");
    }

    @Test
    void shouldRejectRemovedVariableInContextualizationPrompt() {
        gateway.addAgent(10L, true, 101L);
        gateway.addVersion(10L, 101L);
        models.put(601L, new ModelInstanceReferenceBO(601L, ModelType.CHAT, EnableStatus.ACTIVE));
        List<PromptService.PromptVariableReference> variables = List.of(
                new PromptService.PromptVariableReference("query.rewrite_history", PromptVariableType.MESSAGE_LIST));
        prompts.put(701L, new PromptService.PromptVersionReference(701L, PromptRole.SYSTEM, true, variables));
        ApplicationDefinition draft = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(new ApplicationAgentReference(40L, null, null, null))
                .contextPolicy(ApplicationContextPolicy.builder()
                        .contextualization(ContextualizationPolicy.builder()
                                .enabled(true)
                                .chatModelInstanceId(601L)
                                .promptVersionId(701L)
                                .build())
                        .budget(ContextBudgetPolicy.defaults())
                        .build())
                .build();

        ApplicationDefinitionResolution result = validator.resolveForPublish(draft);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ApplicationValidationIssueVO::code)
                .contains("CONTEXTUALIZATION_PROMPT_VARIABLE_UNSUPPORTED");
    }

    private static ApplicationDefinition definition(Long versionId) {
        return ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(new ApplicationAgentReference(40L, versionId, null, null))
                .build();
    }

    private static WorkflowDefinition workflowDefinition() {
        return new WorkflowDefinition(1, "refund", List.of(), List.of());
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

    private static final class StubAgentReferenceGateway
            implements com.lingxi.workbench.admin.agent.service.AgentReferenceService {
        private final Map<Long, AgentReference> agents = new HashMap<>();
        private final Map<String, Long> versions = new HashMap<>();

        private StubAgentReferenceGateway() {
            addAgent(40L, true, 401L, AgentType.REACT);
            addVersion(40L, 401L);
        }

        void addAgent(Long id, boolean active, Long publishedVersionId) {
            addAgent(id, active, publishedVersionId, AgentType.REACT);
        }

        void addAgent(Long id, boolean active, Long publishedVersionId, AgentType agentType) {
            agents.put(id, new AgentReference(new AgentIdentity(id, "Agent-" + id, "Agent职责-" + id, agentType), active, publishedVersionId));
        }

        void addVersion(Long agentId, Long versionId) {
            versions.put(key(agentId, versionId), versionId);
        }

        @Override
        public Optional<AgentReference> findAgent(Long agentId) {
            return Optional.ofNullable(agents.get(agentId));
        }

        @Override
        public Optional<Long> findVersion(Long agentId, Long versionId) {
            return Optional.ofNullable(versions.get(key(agentId, versionId)));
        }

    }
}

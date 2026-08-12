package com.lingxi.workbench.workflow.model;

import com.alibaba.fastjson2.JSON;
import com.lingxi.workbench.workflow.model.config.AgentNodeConfig;
import com.lingxi.workbench.workflow.model.config.EndNodeConfig;
import com.lingxi.workbench.workflow.model.config.IntentClassifierNodeConfig;
import com.lingxi.workbench.workflow.model.config.StartNodeConfig;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import com.lingxi.workbench.workflow.service.WorkflowDefinitionValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;

class WorkflowDefinitionJsonTest {

    @Test
    void shouldRoundTripPolymorphicNodeConfigs() {
        WorkflowDefinition source = new WorkflowDefinition(
                1,
                "main",
                List.of(
                        new WorkflowNodeDefinition(
                                "start", "开始", WorkflowNodeType.START, new StartNodeConfig()),
                        new WorkflowNodeDefinition(
                                "intent", "识别意图", WorkflowNodeType.INTENT_CLASSIFIER,
                                new IntentClassifierNodeConfig(
                                        10L,
                                        11L,
                                        "query",
                                        Map.of("intentCode", "intentCode"))),
                        new WorkflowNodeDefinition(
                                "agent",
                                "问答Agent",
                                WorkflowNodeType.AGENT,
                                new AgentNodeConfig(
                                        1L,
                                        2L,
                                        Map.of("query", "query"),
                                        Map.of("answer", "answer"))),
                        new WorkflowNodeDefinition(
                                "end",
                                "结束",
                                WorkflowNodeType.END,
                                new EndNodeConfig(Map.of("response", "answer")))),
                List.of(
                        WorkflowEdgeDefinition.direct("start", "intent"),
                        WorkflowEdgeDefinition.direct("intent", "agent"),
                        WorkflowEdgeDefinition.direct("agent", "end")));

        String json = JSON.toJSONString(source);
        WorkflowDefinition restored = JSON.parseObject(json, WorkflowDefinition.class);

        assertThat(restored).isEqualTo(source);
        assertThat(json).contains("\"configType\":\"AGENT\"");
        assertThat(json).contains("\"configType\":\"INTENT_CLASSIFIER\"");
    }

    @Test
    void shouldRoundTripApplicationDefinitionStoredAsJsonb() {
        ApplicationAgentReference supervisor = new ApplicationAgentReference(9007199254740993L, 9007199254740995L, "协调Agent", "负责规划与汇总");
        ApplicationAgentReference member = new ApplicationAgentReference(9007199254740997L, 9007199254740999L, "售后专家", "处理售后问题");
        AgentCollaborationDefinition collaboration = new AgentCollaborationDefinition(
                true, supervisor, List.of(member), new AgentCollaborationLimits(1));
        ApplicationCapabilityDefinition fallbackCap = ApplicationCapabilityDefinition.builder()
                .code("default_agent")
                .type(ApplicationCapabilityType.AGENT)
                .targetId(member.agentId())
                .targetVersionId(member.agentVersionId())
                .build();
        ApplicationDefinition source = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .collaboration(collaboration)
                .fallback(new FallbackAgent("fallback_agent"))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder().capabilities(List.of(fallbackCap)).build())
                .build();

        String json = JSON.toJSONString(source);
        ApplicationDefinition restored =
                JSON.parseObject(json, ApplicationDefinition.class);

        assertThat(restored).isEqualTo(source);
        assertThat(json).contains("\"agentId\":\"9007199254740993\"");
        assertThat(restored.collaboration().members().getFirst().agentId()).isEqualTo(9007199254740997L);
        assertThat(json).contains("\"agentVersionId\":\"9007199254740999\"");
    }

    @Test
    void shouldAllowUnlockedAgentVersionOnlyForDraftValidation() {
        WorkflowDefinition draft = new WorkflowDefinition(
                1,
                "main",
                List.of(
                        new WorkflowNodeDefinition(
                                "start", "开始", WorkflowNodeType.START, new StartNodeConfig()),
                        new WorkflowNodeDefinition(
                                "agent",
                                "问答Agent",
                                WorkflowNodeType.AGENT,
                                new AgentNodeConfig(
                                        1L,
                                        null,
                                        Map.of("query", "query"),
                                        Map.of("answer", "answer"))),
                        new WorkflowNodeDefinition(
                                "end",
                                "结束",
                                WorkflowNodeType.END,
                                new EndNodeConfig(Map.of("response", "answer")))),
                List.of(
                        WorkflowEdgeDefinition.direct("start", "agent"),
                        WorkflowEdgeDefinition.direct("agent", "end")));

        WorkflowDefinitionValidator validator = new WorkflowDefinitionValidator();

        assertThatCode(() -> validator.validateDraft(draft)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(draft))
                .hasMessageContaining("agentVersionId");
    }

    @Test
    void shouldRoundTripApplicationContextPolicy() {
        ContextualizationPolicy contextualization = ContextualizationPolicy.builder()
                .enabled(true)
                .chatModelInstanceId(11L)
                .promptVersionId(12L)
                .fallbackToOriginal(false)
                .maxOutputTokens(256)
                .maxOutputCharacters(1000)
                .timeoutSeconds(15)
                .build();
        ApplicationDefinition source = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .contextPolicy(ApplicationContextPolicy.builder()
                        .contextualization(contextualization)
                        .budget(ContextBudgetPolicy.defaults())
                        .build())
                .build();

        String json = JSON.toJSONString(source);
        ApplicationDefinition restored = JSON.parseObject(json, ApplicationDefinition.class);

        assertThat(restored).isEqualTo(source);
        assertThat(restored.contextPolicy().contextualization().getPromptVersionId()).isEqualTo(12L);
        assertThat(restored.contextPolicy().budget().getMaxObservationTokens()).isEqualTo(16000);
    }

    @Test
    void shouldRoundTripApplicationCapabilityCatalog() {
        ApplicationCapabilityDefinition capability = ApplicationCapabilityDefinition.builder()
                .code("fallback_agent")
                .type(ApplicationCapabilityType.AGENT)
                .name("默认助手")
                .description("处理通用请求")
                .targetId(10L)
                .targetVersionId(11L)
                .riskLevel(CapabilityRiskLevel.READ_ONLY)
                .timeoutSeconds(120)
                .maxCalls(1)
                .resultTokenBudget(8192)
                .build();
        ApplicationDefinition source = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .fallback(new FallbackAgent("fallback_agent"))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder().capabilities(List.of(capability)).build())
                .runtimePolicy(ApplicationRuntimePolicy.defaults())
                .build();

        String json = JSON.toJSONString(source);
        ApplicationDefinition restored = JSON.parseObject(json, ApplicationDefinition.class);

        assertThat(restored).isEqualTo(source);
        assertThat(restored.fallback().cap()).isEqualTo("fallback_agent");
        assertThat(json).contains("\"fallback\":{\"cap\":\"fallback_agent\"}");
        assertThat(restored.capabilityCatalog().capabilities()).singleElement()
                .satisfies(item -> assertThat(item.getCode()).isEqualTo("fallback_agent"));
        assertThat(json).contains("\"targetVersionId\":\"11\"");
    }

    @Test
    void shouldRoundTripApplicationOptionalFastRoutes() {
        ApplicationIntentMapping mapping = ApplicationIntentMapping.builder()
                .intentCode("refund")
                .targetType(IntentExecutionTargetType.WORKFLOW)
                .capabilityCode("refund_workflow")
                .targetId(20L)
                .targetVersionId(21L)
                .build();
        ApplicationDefinition source = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .optionalFastRoutes(ApplicationIntentBinding.builder()
                        .enabled(true)
                        .intentTreeId(30L)
                        .intentTreeVersionId(31L)
                        .mappings(List.of(mapping))
                        .build())
                .build();

        String json = JSON.toJSONString(source);
        ApplicationDefinition restored = JSON.parseObject(json, ApplicationDefinition.class);

        assertThat(restored).isEqualTo(source);
        assertThat(restored.optionalFastRoutes().mappings()).singleElement()
                .satisfies(item -> assertThat(item.getCapabilityCode()).isEqualTo("refund_workflow"));
    }
}

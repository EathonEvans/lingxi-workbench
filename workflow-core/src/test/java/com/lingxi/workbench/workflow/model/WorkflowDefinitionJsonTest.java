package com.lingxi.workbench.workflow.model;

import com.alibaba.fastjson2.JSON;
import com.lingxi.workbench.workflow.model.config.AgentNodeConfig;
import com.lingxi.workbench.workflow.model.config.EndNodeConfig;
import com.lingxi.workbench.workflow.model.config.StartNodeConfig;
import com.lingxi.workbench.workflow.service.WorkflowDefinitionValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                        WorkflowEdgeDefinition.direct("start", "agent"),
                        WorkflowEdgeDefinition.direct("agent", "end")));

        String json = JSON.toJSONString(source);
        WorkflowDefinition restored = JSON.parseObject(json, WorkflowDefinition.class);

        assertThat(restored).isEqualTo(source);
        assertThat(json).contains("\"configType\":\"AGENT\"");
    }

    @Test
    void shouldRoundTripApplicationDefinitionStoredAsJsonb() {
        WorkflowDefinition workflow = new WorkflowDefinition(
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
                                        9007199254740993L,
                                        9007199254740995L,
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
        ApplicationDefinition source = new ApplicationDefinition(1, workflow);

        String json = JSON.toJSONString(source);
        ApplicationDefinition restored =
                JSON.parseObject(json, ApplicationDefinition.class);

        assertThat(restored).isEqualTo(source);
        assertThat(json).contains("\"agentId\":\"9007199254740993\"");
        AgentNodeConfig restoredConfig = (AgentNodeConfig)
                restored.workflow().nodes().get(1).config();
        assertThat(restoredConfig.agentId()).isEqualTo(9007199254740993L);
        assertThat(restoredConfig.agentVersionId()).isEqualTo(9007199254740995L);
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
}

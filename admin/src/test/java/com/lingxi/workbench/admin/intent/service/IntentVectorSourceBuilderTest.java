package com.lingxi.workbench.admin.intent.service;

import com.lingxi.workbench.workflow.model.intent.IntentNodeDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentDecisionFailureAction;
import com.lingxi.workbench.workflow.model.intent.IntentNodeType;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntentVectorSourceBuilderTest {

    private final IntentVectorSourceBuilder builder = new IntentVectorSourceBuilder();

    @Test
    void shouldBuildPrototypeAndDistinctExamplesForEnabledLeaves() {
        IntentNodeDefinition leaf = IntentNodeDefinition.builder()
                .id("refund-node")
                .code("refund")
                .name("申请退款")
                .description("用户希望退回已支付款项")
                .nodeType(IntentNodeType.LEAF)
                .positiveExamples(List.of("我要退款", "我要退款", " 退回费用 "))
                .enabled(true)
                .build();
        IntentTreeDefinition definition = new IntentTreeDefinition(
                1,
                10L,
                IntentRoutingPolicy.builder()
                        .topK(5)
                        .recallThreshold(new BigDecimal("0.65"))
                        .chatModelInstanceId(9L)
                        .decisionPromptVersionId(10L)
                        .maxOutputTokens(512)
                        .timeoutSeconds(30)
                        .maxRepeatAttempts(1)
                        .decisionFailureAction(IntentDecisionFailureAction.DEFER)
                        .build(),
                List.of(leaf));

        List<IntentVectorSourceBuilder.IntentVectorSource> sources = builder.build(definition);

        assertThat(sources).hasSize(3);
        assertThat(sources).extracting(IntentVectorSourceBuilder.IntentVectorSource::vectorKind)
                .containsExactly("PROTOTYPE", "EXAMPLE", "EXAMPLE");
        assertThat(sources).extracting(IntentVectorSourceBuilder.IntentVectorSource::text)
                .contains("我要退款", "退回费用");
        assertThat(sources).extracting(IntentVectorSourceBuilder.IntentVectorSource::sourceHash)
                .allMatch(hash -> hash.length() == 64);
    }
}

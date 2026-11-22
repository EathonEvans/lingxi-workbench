package com.lingxi.workbench.admin.agent.service;

import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.admin.agent.model.config.AgentDefinition;
import com.lingxi.workbench.admin.agent.model.config.GenerationPolicy;
import com.lingxi.workbench.admin.agent.model.config.KnowledgeSearchCapabilitySnapshot;
import com.lingxi.workbench.admin.agent.model.config.RetrievalPolicy;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import com.lingxi.workbench.infra.ai.rerank.model.RerankPolicy;
import com.lingxi.workbench.framework.json.JSON;
import static org.assertj.core.api.Assertions.assertThat;

class AgentSnapshotChecksumTest {
    private final AgentSnapshotChecksum checksum = new AgentSnapshotChecksum();

    @Test
    void shouldGenerateStableChecksumAndDetectBehaviorChange() {
        AgentDefinition first = definition(5);
        AgentDefinition same = definition(5);
        AgentDefinition changed = definition(10);

        assertThat(checksum.calculate(first))
                .hasSize(64)
                .isEqualTo(checksum.calculate(same))
                .isNotEqualTo(checksum.calculate(changed));
    }

    @Test
    void defaultPoliciesShouldSatisfyBeanValidation() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            assertThat(validator.validate(RetrievalPolicy.defaults())).isEmpty();
            assertThat(validator.validate(GenerationPolicy.defaults())).isEmpty();
        }
    }

    @Test
    void rerankMustParticipateInSnapshotChecksumAndJsonRoundTrip() {
        AgentDefinition original = definition(5);
        AgentDefinition ranked = definition(5);
        ranked.getKnowledgeSearch().setRerank(new RerankPolicy(9L, 20, 0.5));
        AgentDefinition restored = JSON.parseObject(JSON.toJSONString(ranked), AgentDefinition.class);
        assertThat(restored.getKnowledgeSearch().getRerank()).isEqualTo(new RerankPolicy(9L, 20, 0.5));
        assertThat(checksum.calculate(ranked)).isEqualTo(checksum.calculate(restored))
                .isNotEqualTo(checksum.calculate(original));
        restored.getKnowledgeSearch().setRerank(new RerankPolicy(9L, 30, 0.5));
        assertThat(checksum.calculate(restored)).isNotEqualTo(checksum.calculate(ranked));
    }

    private static AgentDefinition definition(int topK) {
        return AgentDefinition.builder()
                .schemaVersion(6)
                .role(AgentRole.SPECIALIST)
                .chatModelInstanceId(10L)
                .systemPromptVersionId(20L)
                .userPromptVersionId(21L)
                .knowledgeSearch(KnowledgeSearchCapabilitySnapshot.builder()
                        .knowledgeBaseId(30L)
                        .knowledgeIndexVersionId(31L)
                        .embeddingModelInstanceId(40L)
                        .topK(topK)
                        .maxResultTokens(4000)
                        .build())
                .generationPolicy(GenerationPolicy.defaults())
                .build();
    }
}

package com.lingxi.workbench.admin.agent.service;

import com.lingxi.workbench.admin.agent.common.enums.AgentType;
import com.lingxi.workbench.admin.agent.common.enums.KnowledgeBindingMode;
import com.lingxi.workbench.admin.agent.model.config.AgentDefinition;
import com.lingxi.workbench.admin.agent.model.config.AgentKnowledgeBinding;
import com.lingxi.workbench.admin.agent.model.config.AgentMemoryPolicy;
import com.lingxi.workbench.admin.agent.model.config.AgentRuntimePolicy;
import com.lingxi.workbench.admin.agent.model.config.GenerationPolicy;
import com.lingxi.workbench.admin.agent.model.config.QueryRewritePolicy;
import com.lingxi.workbench.admin.agent.model.config.RetrievalPolicy;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

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
            assertThat(validator.validate(AgentMemoryPolicy.defaults())).isEmpty();
            assertThat(validator.validate(QueryRewritePolicy.defaults())).isEmpty();
            assertThat(validator.validate(RetrievalPolicy.defaults())).isEmpty();
            assertThat(validator.validate(GenerationPolicy.defaults())).isEmpty();
            assertThat(validator.validate(AgentRuntimePolicy.defaults())).isEmpty();
        }
    }

    private static AgentDefinition definition(int topK) {
        RetrievalPolicy retrieval = RetrievalPolicy.defaults();
        retrieval.setTopK(topK);
        return AgentDefinition.builder()
                .schemaVersion(2)
                .agentType(AgentType.RAG_CHAT)
                .chatModelInstanceId(10L)
                .systemPromptVersionId(20L)
                .userPromptVersionId(21L)
                .knowledge(AgentKnowledgeBinding.builder()
                        .knowledgeBaseId(30L)
                        .embeddingModelInstanceId(40L)
                        .bindingMode(KnowledgeBindingMode.LATEST)
                        .priority(0)
                        .build())
                .memoryPolicy(AgentMemoryPolicy.defaults())
                .rewritePolicy(QueryRewritePolicy.defaults())
                .retrievalPolicy(retrieval)
                .generationPolicy(GenerationPolicy.defaults())
                .runtimePolicy(AgentRuntimePolicy.defaults())
                .build();
    }
}

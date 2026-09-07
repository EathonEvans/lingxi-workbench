package com.lingxi.workbench.knowledge.evaluation;

import com.lingxi.workbench.knowledge.config.KnowledgeRetrievalProperties;
import com.lingxi.workbench.knowledge.service.KnowledgeSearchService;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 确认离线入口装配的是完整检索链路，同时不启动 Web 或对话服务。 */
class KnowledgeEvaluationConfigurationTest {
    @Test
    void shouldWireReadOnlyCliWithValidatedPlatformLimits() {
        new ApplicationContextRunner().withUserConfiguration(KnowledgeEvaluationConfiguration.class)
                .withPropertyValues("lingxi.knowledge-evaluation.enabled=true", "lingxi.security.secret-master-key=" + java.util.Base64.getEncoder().encodeToString(new byte[32]), "lingxi.knowledge.retrieval.max-document-tokens=1000")
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(KnowledgeEvaluationCommand.class)
                            .hasSingleBean(KnowledgeSearchService.class);
                    assertThat(context.getBean(KnowledgeRetrievalProperties.class).getMaxDocumentTokens()).isEqualTo(1000);
                    assertThat(context.containsBean("chatStreamServiceImpl")).isFalse();
                });
    }

    @Test
    void invalidPlatformLimitMustFailAtStartup() {
        new ApplicationContextRunner().withUserConfiguration(KnowledgeEvaluationConfiguration.class)
                .withPropertyValues("lingxi.knowledge-evaluation.enabled=true", "lingxi.security.secret-master-key=" + java.util.Base64.getEncoder().encodeToString(new byte[32]), "lingxi.knowledge.retrieval.max-document-tokens=0")
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("maxDocumentTokens");
                });
    }
}

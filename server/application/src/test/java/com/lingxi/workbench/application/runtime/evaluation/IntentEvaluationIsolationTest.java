package com.lingxi.workbench.application.runtime.evaluation;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.lingxi.workbench.application.runtime.repo.ApplicationRuntimeRepo;
import com.lingxi.workbench.application.runtime.repo.IntentTreeRuntimeRepo;
import com.lingxi.workbench.application.runtime.repo.IntentVectorRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.routing.IntentCandidateRetriever;
import com.lingxi.workbench.model.repo.ModelRuntimeConfigRepo;
import com.lingxi.workbench.model.service.impl.ModelRuntimeConfigServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;

class IntentEvaluationIsolationTest {

    @Test
    void shouldOnlyActivateEvaluationConfigurationInExplicitEvaluationMode() {
        ConditionalOnProperty condition = IntentEvaluationConfiguration.class
                .getAnnotation(ConditionalOnProperty.class);

        assertThat(condition.name()).containsExactly("lingxi.intent-evaluation.enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
    }

    @Test
    void shouldImportOnlyRequiredInfrastructureAutoConfigurations() {
        ImportAutoConfiguration configuration = IntentEvaluationConfiguration.class
                .getAnnotation(ImportAutoConfiguration.class);

        assertThat(configuration.value()).containsExactlyInAnyOrder(
                DataSourceAutoConfiguration.class,
                JdbcTemplateAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                TransactionAutoConfiguration.class,
                MybatisPlusAutoConfiguration.class);
    }

    @Test
    void shouldUseExplicitEvaluationDependencyClosureWithoutComponentScanning() {
        Import imported = IntentEvaluationConfiguration.class.getAnnotation(Import.class);

        assertThat(IntentEvaluationConfiguration.class.getAnnotation(ComponentScan.class)).isNull();
        assertThat(imported.value()).contains(
                IntentEvaluationCommand.class,
                IntentEvaluationService.class,
                IntentRoutingEvaluationService.class,
                IntentRoutingVersionPreviewGateway.class,
                ApplicationRuntimeRepo.class,
                IntentTreeRuntimeRepo.class,
                IntentVectorRuntimeRepo.class,
                IntentCandidateRetriever.class,
                ModelRuntimeConfigRepo.class,
                ModelRuntimeConfigServiceImpl.class);
    }

    @Test
    void shouldOnlyKeepOfflineCommandOutsideProductionComponentScan() {
        assertThat(AnnotatedElementUtils.findMergedAnnotation(IntentEvaluationCommand.class, Component.class)).isNull();
        assertThat(AnnotatedElementUtils.findMergedAnnotation(IntentEvaluationService.class, Component.class)).isNotNull();
    }
}

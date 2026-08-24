package com.lingxi.workbench.application.runtime.evaluation;

import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntentEvaluationCommandArgumentsTest {

    @Test
    void shouldParseExportArguments() {
        IntentEvaluationCommandArguments result = IntentEvaluationCommandArguments.parse(new String[]{
                "export",
                "--application-id=10",
                "--application-version-id=20",
                "--dataset-version=v1-baseline",
                "--output=/tmp/intent-dataset.json"
        });

        assertThat(result.operation()).isEqualTo(IntentEvaluationCommandOperation.EXPORT);
        assertThat(result.requireLong("application-id")).isEqualTo(10L);
        assertThat(result.requirePath("output").toString()).isEqualTo("/tmp/intent-dataset.json");
    }

    @Test
    void shouldRejectUnknownOption() {
        assertThatThrownBy(() -> IntentEvaluationCommandArguments.parse(new String[]{
                "evaluate",
                "--dataset=/tmp/intent-dataset.json",
                "--output=/tmp/intent-report.json",
                "--spring.profiles.active=production"
        }))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("意图评测命令包含未知参数");
    }

    @Test
    void shouldRejectNonPositiveResourceId() {
        IntentEvaluationCommandArguments result = IntentEvaluationCommandArguments.parse(new String[]{
                "export",
                "--application-id=0"
        });

        assertThatThrownBy(() -> result.requireLong("application-id"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("命令参数--application-id必须为正整数");
    }

    @Test
    void shouldParseV2EvaluationOperation() {
        IntentEvaluationCommandArguments result = IntentEvaluationCommandArguments.parse(new String[]{
                "evaluate-v2",
                "--dataset=/tmp/intent-v2-dataset.json",
                "--output=/tmp/intent-v2-report.json"
        });

        assertThat(result.operation()).isEqualTo(IntentEvaluationCommandOperation.EVALUATE_V2);
    }
}

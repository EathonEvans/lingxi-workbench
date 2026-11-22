package com.lingxi.workbench.knowledge.evaluation;

import com.lingxi.workbench.framework.json.JSON;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KnowledgeEvaluationCommandTest {
    @TempDir Path directory;

    @Test
    void shouldReadTypedDatasetAndWriteNewReport() throws Exception {
        Path dataset = directory.resolve("dataset.json");
        Path output = directory.resolve("report.json");
        Files.writeString(dataset, JSON.toJSONString(KnowledgeEvaluationServiceTest.dataset(
                KnowledgeEvaluationServiceTest.sample("one", Set.of(10L), false))));
        KnowledgeEvaluationService service = mock(KnowledgeEvaluationService.class);
        when(service.evaluate(any())).thenReturn(KnowledgeEvaluationReport.builder().datasetVersion("v1").passed(true).samples(List.of()).build());
        assertThat(new KnowledgeEvaluationCommand(service).execute(arguments(dataset, output)).isPassed()).isTrue();
        assertThat(Files.readString(output)).contains("\"passed\": true").doesNotContain("private query");
        ArgumentCaptor<KnowledgeEvaluationDataset> captured = ArgumentCaptor.forClass(KnowledgeEvaluationDataset.class);
        verify(service).evaluate(captured.capture());
        assertThat(captured.getValue().samples().getFirst().request().getKnowledgeBaseId()).isEqualTo(1L);
    }

    @Test
    void existingReportAndOversizedDatasetMustFailBeforeModelCalls() throws Exception {
        Path dataset = directory.resolve("dataset.json");
        Path output = directory.resolve("report.json");
        KnowledgeEvaluationService service = mock(KnowledgeEvaluationService.class);
        Files.writeString(output, "keep me");
        assertThatThrownBy(() -> new KnowledgeEvaluationCommand(service).execute(arguments(dataset, output))).hasMessageContaining("已存在");
        assertThat(Files.readString(output)).isEqualTo("keep me");
        Files.write(dataset, new byte[1048577]);
        assertThatThrownBy(() -> new KnowledgeEvaluationCommand(service).execute(arguments(dataset, directory.resolve("new.json"))))
                .hasMessageContaining("超过 1 MiB");
        verifyNoInteractions(service);
    }

    private static String[] arguments(Path dataset, Path output) {
        return new String[]{"--dataset=" + dataset, "--output=" + output};
    }
}

package com.lingxi.workbench.application.runtime.evaluation;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntentEvaluationCommandTest {

    private final IntentEvaluationService service = mock(IntentEvaluationService.class);
    private final IntentRoutingEvaluationService routingEvaluationService = mock(IntentRoutingEvaluationService.class);
    private final IntentEvaluationCommand command = new IntentEvaluationCommand(service, routingEvaluationService);

    @TempDir
    private Path temporaryDirectory;

    @Test
    void shouldWriteExportedDatasetAsPrettyJson() throws Exception {
        long applicationVersionId = 2091785020013432832L;
        Path output = temporaryDirectory.resolve("dataset.json");
        when(service.exportDatasetTemplate(4L, applicationVersionId, "v1-baseline"))
                .thenReturn(IntentEvaluationDataset.builder()
                        .schemaVersion(IntentEvaluationDataset.SCHEMA_VERSION)
                        .datasetVersion("v1-baseline")
                        .applicationId(4L)
                        .applicationVersionId(applicationVersionId)
                        .intentTreeId(1L)
                        .intentTreeVersionId(3L)
                        .embeddingModelInstanceId(4L)
                        .leafMetadata(List.of())
                        .samples(List.of())
                        .build());

        command.execute(new String[]{
                "export",
                "--application-id=4",
                "--application-version-id=" + applicationVersionId,
                "--dataset-version=v1-baseline",
                "--output=" + output
        });

        String outputJson = Files.readString(output);
        IntentEvaluationDataset dataset = JSON.parseObject(outputJson, IntentEvaluationDataset.class);
        assertThat(dataset.getApplicationVersionId()).isEqualTo(applicationVersionId);
        assertThat(outputJson)
                .contains("\n")
                .contains("\"applicationVersionId\":\"" + applicationVersionId + "\"");
    }

    @Test
    void shouldReadDatasetAndWriteEvaluationReport() throws Exception {
        Path datasetPath = temporaryDirectory.resolve("dataset.json");
        Path output = temporaryDirectory.resolve("report.json");
        IntentEvaluationDataset dataset = IntentEvaluationDataset.builder()
                .schemaVersion(IntentEvaluationDataset.SCHEMA_VERSION)
                .datasetVersion("v1-baseline")
                .applicationId(4L)
                .applicationVersionId(21L)
                .intentTreeId(1L)
                .intentTreeVersionId(3L)
                .embeddingModelInstanceId(4L)
                .samples(List.of())
                .build();
        Files.writeString(datasetPath, JSON.toJSONString(dataset));
        when(service.evaluate(any())).thenReturn(IntentEvaluationReport.builder()
                .schemaVersion(IntentEvaluationDataset.SCHEMA_VERSION)
                .datasetVersion("v1-baseline")
                .generatedAt(Instant.parse("2026-08-24T00:00:00Z"))
                .applicationId(4L)
                .applicationVersionId(21L)
                .intentTreeId(1L)
                .intentTreeVersionId(3L)
                .embeddingModelInstanceId(4L)
                .sampleCount(100)
                .metrics(IntentEvaluationMetrics.builder().finalActionAccuracy(0.8D).build())
                .confusionMatrix(List.of())
                .failures(List.of())
                .build());

        command.execute(new String[]{
                "evaluate",
                "--dataset=" + datasetPath,
                "--output=" + output
        });

        JSONObject report = JSON.parseObject(Files.readString(output));
        assertThat(report.getInteger("sampleCount")).isEqualTo(100);
        assertThat(report.getJSONObject("metrics").getDouble("finalActionAccuracy")).isEqualTo(0.8D);
    }

    @Test
    void shouldReadV2DatasetAndWriteRoutingReport() throws Exception {
        Path datasetPath = temporaryDirectory.resolve("dataset-v2.json");
        Path output = temporaryDirectory.resolve("report-v2.json");
        IntentRoutingEvaluationDataset dataset = IntentRoutingEvaluationDataset.builder()
                .schemaVersion(2)
                .datasetVersion("v2-production")
                .samples(List.of())
                .build();
        Files.writeString(datasetPath, JSON.toJSONString(dataset));
        when(routingEvaluationService.evaluate(any())).thenReturn(IntentRoutingEvaluationReport.builder()
                .schemaVersion(2)
                .datasetVersion("v2-production")
                .sampleCount(300)
                .productionGatePassed(true)
                .gates(List.of())
                .confusionMatrix(List.of())
                .failures(List.of())
                .build());

        command.execute(new String[]{
                "evaluate-v2",
                "--dataset=" + datasetPath,
                "--output=" + output
        });

        JSONObject report = JSON.parseObject(Files.readString(output));
        assertThat(report.getInteger("schemaVersion")).isEqualTo(2);
        assertThat(report.getBoolean("productionGatePassed")).isTrue();
    }
}

package com.lingxi.workbench.knowledge.evaluation;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.knowledge.model.bo.KnowledgeSearchHit;
import com.lingxi.workbench.knowledge.model.bo.KnowledgeSearchResponse;
import com.lingxi.workbench.knowledge.service.KnowledgeSearchService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** 复用在线检索服务的离线评测，不创建绕过权限的检索入口。 */
@RequiredArgsConstructor
public class KnowledgeEvaluationService {
    private final KnowledgeSearchService searchService;
    private final Validator validator;

    /** 先验证完整数据集，再顺序调用真实检索链路，避免非法后续样本导致部分外部调用。 */
    public KnowledgeEvaluationReport evaluate(KnowledgeEvaluationDataset dataset) {
        if (Objects.isNull(dataset) || !validator.validate(dataset).isEmpty()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "知识评测数据集不符合约束");
        }
        if (dataset.samples().stream().map(KnowledgeEvaluationSample::id).distinct().count() != dataset.samples().size()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "知识评测样本标识不能重复");
        }
        List<KnowledgeEvaluatedSample> samples = new ArrayList<>();
        for (KnowledgeEvaluationSample sample : dataset.samples()) {
            samples.add(evaluateSample(sample));
        }
        return KnowledgeEvaluationReport.builder().datasetVersion(dataset.datasetVersion())
                .passed(samples.stream().allMatch(KnowledgeEvaluatedSample::isPassed)).samples(List.copyOf(samples))
                .vectorMetrics(average(samples, KnowledgeEvaluatedSample::getVectorMetrics))
                .finalMetrics(average(samples, KnowledgeEvaluatedSample::getFinalMetrics)).build();
    }

    private KnowledgeEvaluatedSample evaluateSample(KnowledgeEvaluationSample sample) {
        try {
            KnowledgeSearchResponse response = searchService.searchVersion(sample.request().toBuilder().executionId("knowledge-evaluation:" + sample.id()).build());
            List<Long> baseline = response.diagnostics().getCandidates().stream().limit(sample.request().getTopK())
                    .map(KnowledgeSearchHit::chunkId).toList();
            List<Long> selected = response.diagnostics().getSelected().stream().map(KnowledgeSearchHit::chunkId).toList();
            boolean passed = !sample.expectDenied() && (sample.expectedChunkIds().isEmpty()
                    ? selected.isEmpty() : selected.stream().anyMatch(sample.expectedChunkIds()::contains));
            return KnowledgeEvaluatedSample.builder().id(sample.id()).passed(passed).diagnostics(response.diagnostics())
                    .vectorMetrics(metrics(baseline, sample.expectedChunkIds())).finalMetrics(metrics(selected, sample.expectedChunkIds())).build();
        } catch (BusinessException exception) {
            return failed(sample, exception.getCode(), exception.getHttpStatus() == HttpStatusEnum.FORBIDDEN && sample.expectDenied());
        } catch (RuntimeException exception) {
            // 失败独立计入报告，绝不以空结果或零延迟冒充一次成功检索。
            return failed(sample, HttpStatusEnum.INTERNAL_SERVER_ERROR.getCode(), false);
        }
    }

    private static KnowledgeEvaluatedSample failed(KnowledgeEvaluationSample sample, int code, boolean passed) {
        KnowledgeEvaluationMetrics zero = metrics(List.of(), sample.expectedChunkIds());
        return KnowledgeEvaluatedSample.builder().id(sample.id()).passed(passed).errorCode(code).vectorMetrics(zero).finalMetrics(zero).build();
    }

    private static KnowledgeEvaluationMetrics metrics(List<Long> retrieved, Set<Long> expected) {
        if (expected.isEmpty()) return null;
        double recall = (double) retrieved.stream().distinct().filter(expected::contains).count() / expected.size();
        double reciprocalRank = 0;
        for (int index = 0; index < retrieved.size(); index++) {
            if (expected.contains(retrieved.get(index))) {
                reciprocalRank = 1.0 / (index + 1);
                break;
            }
        }
        return new KnowledgeEvaluationMetrics(recall, reciprocalRank);
    }

    private static KnowledgeEvaluationMetrics average(List<KnowledgeEvaluatedSample> samples, Function<KnowledgeEvaluatedSample, KnowledgeEvaluationMetrics> selector) {
        List<KnowledgeEvaluationMetrics> scores = samples.stream().map(selector).filter(Objects::nonNull).toList();
        if (scores.isEmpty()) return null;
        return new KnowledgeEvaluationMetrics(scores.stream().mapToDouble(KnowledgeEvaluationMetrics::recallAtK).average().orElseThrow(),
                scores.stream().mapToDouble(KnowledgeEvaluationMetrics::mrr).average().orElseThrow());
    }
}

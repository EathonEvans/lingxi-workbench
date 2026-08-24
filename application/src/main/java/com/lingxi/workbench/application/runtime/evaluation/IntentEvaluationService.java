package com.lingxi.workbench.application.runtime.evaluation;

import com.lingxi.workbench.application.runtime.model.IntentCandidateRequest;
import com.lingxi.workbench.application.runtime.model.IntentCandidateSet;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeApplicationVersionEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.application.runtime.repo.ApplicationRuntimeRepo;
import com.lingxi.workbench.application.runtime.repo.IntentTreeRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.routing.IntentCandidateRetriever;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentNodeDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentNodeType;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 意图候选召回的只读数据集导出和离线评测服务。 */
@RequiredArgsConstructor
public class IntentEvaluationService {

    private static final int MINIMUM_BASELINE_SAMPLE_COUNT = 100;

    private final ApplicationRuntimeRepo applicationRuntimeRepo;
    private final IntentTreeRuntimeRepo intentTreeRuntimeRepo;
    private final IntentCandidateRetriever candidateRetriever;

    /**
     * 导出指定 ApplicationVersion 锁定的意图树叶子和映射元数据，供人工编写样本。
     *
     * @param applicationId        Application 主键
     * @param applicationVersionId 已发布 ApplicationVersion 主键
     * @param datasetVersion       人工维护的数据集版本标识
     * @return 尚未填充 samples 的严格版本化模板
     */
    public IntentEvaluationDataset exportDatasetTemplate(
            Long applicationId,
            Long applicationVersionId,
            String datasetVersion) {
        RuntimeApplicationVersionEntity applicationVersion = loadApplicationVersion(applicationId, applicationVersionId);
        ApplicationIntentBinding binding = requireBinding(applicationVersion.getDefinitionSnapshot());
        RuntimeIntentTreeVersionEntity intentTreeVersion = loadIntentTreeVersion(
                binding.getIntentTreeId(), binding.getIntentTreeVersionId());
        IntentTreeDefinition definition = requireIntentTreeDefinition(intentTreeVersion);
        Map<String, ApplicationIntentMapping> mappings = binding.mappings().stream()
                .collect(Collectors.toMap(ApplicationIntentMapping::getIntentCode, mapping -> mapping));
        List<IntentEvaluationLeafMetadata> leaves = definition.nodes().stream()
                .filter(node -> node.getNodeType() == IntentNodeType.LEAF)
                .filter(IntentNodeDefinition::isEnabled)
                .sorted(Comparator.comparing(IntentNodeDefinition::getCode))
                .map(node -> toLeafMetadata(node, mappings.get(node.getCode())))
                .toList();
        if (leaves.isEmpty()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "锁定的意图树版本没有启用叶子意图");
        }
        return IntentEvaluationDataset.builder()
                .schemaVersion(IntentEvaluationDataset.SCHEMA_VERSION)
                .datasetVersion(requireText(datasetVersion, "datasetVersion不能为空"))
                .applicationId(applicationId)
                .applicationVersionId(applicationVersionId)
                .intentTreeId(intentTreeVersion.getIntentTreeId())
                .intentTreeVersionId(intentTreeVersion.getId())
                .embeddingModelInstanceId(intentTreeVersion.getEmbeddingModelInstanceId())
                .leafMetadata(leaves)
                .samples(List.of())
                .build();
    }

    /**
     * 使用候选召回结果执行离线评测，不写入任何业务表。
     *
     * <p>动作只是在评测进程内按候选数量投影，生产路由不消费该投影。</p>
     *
     * @param dataset 已锁定版本的评测数据集
     * @return 可保存并用于版本对比的基线报告
     */
    public IntentEvaluationReport evaluate(IntentEvaluationDataset dataset) {
        validateDataset(dataset);
        validateSampleStructure(dataset.samples());
        RuntimeApplicationVersionEntity applicationVersion = loadApplicationVersion(
                dataset.getApplicationId(), dataset.getApplicationVersionId());
        ApplicationIntentBinding binding = requireBinding(applicationVersion.getDefinitionSnapshot());
        validateVersionBinding(dataset, binding);
        RuntimeIntentTreeVersionEntity intentTreeVersion = loadIntentTreeVersion(
                dataset.getIntentTreeId(), dataset.getIntentTreeVersionId());
        if (!Objects.equals(dataset.getEmbeddingModelInstanceId(), intentTreeVersion.getEmbeddingModelInstanceId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "评测数据集的Embedding模型与锁定意图树版本不一致");
        }
        IntentTreeDefinition definition = requireIntentTreeDefinition(intentTreeVersion);
        Set<String> leafCodes = definition.nodes().stream()
                .filter(node -> node.getNodeType() == IntentNodeType.LEAF)
                .filter(IntentNodeDefinition::isEnabled)
                .map(IntentNodeDefinition::getCode)
                .collect(Collectors.toSet());
        validateExpectedIntentCodes(dataset.samples(), leafCodes);

        List<SampleEvaluation> evaluations = dataset.samples().stream()
                .map(sample -> evaluateSample(sample, dataset))
                .toList();
        return buildReport(dataset, evaluations);
    }

    private SampleEvaluation evaluateSample(IntentEvaluationSample sample, IntentEvaluationDataset dataset) {
        IntentCandidateSet candidateSet = candidateRetriever.retrieve(new IntentCandidateRequest(
                dataset.getIntentTreeId(), dataset.getIntentTreeVersionId(), sample.getQuery()));
        IntentEvaluationExpectedAction actualAction = toExpectedAction(candidateSet);
        List<IntentEvaluationCandidate> retrievedCandidates = candidateSet.retrievedCandidates().stream()
                .map(candidate -> new IntentEvaluationCandidate(candidate.intentCode(), candidate.similarity()))
                .toList();
        List<IntentEvaluationCandidate> thresholdedCandidates = candidateSet.candidates().stream()
                .map(candidate -> new IntentEvaluationCandidate(candidate.intentCode(), candidate.similarity()))
                .toList();
        return new SampleEvaluation(sample, actualAction, retrievedCandidates, thresholdedCandidates);
    }

    private static IntentEvaluationReport buildReport(
            IntentEvaluationDataset dataset,
            List<SampleEvaluation> evaluations) {
        long candidateExpectationCount = evaluations.stream()
                .mapToLong(item -> item.sample().expectedIntentCodes().size())
                .sum();
        long candidateRecallCount = evaluations.stream()
                .mapToLong(IntentEvaluationService::recalledExpectedIntentCount)
                .sum();
        List<SampleEvaluation> matchedExpectations = evaluations.stream()
                .filter(item -> item.sample().getExpectedAction() == IntentEvaluationExpectedAction.MATCHED)
                .toList();
        long top1CorrectCount = matchedExpectations.stream()
                .filter(IntentEvaluationService::isTop1Correct)
                .count();
        long ambiguityCount = evaluations.stream()
                .filter(item -> item.actualAction() == IntentEvaluationExpectedAction.AMBIGUOUS)
                .count();
        long rejectionCount = evaluations.stream()
                .filter(item -> item.actualAction() == IntentEvaluationExpectedAction.REJECTED)
                .count();
        long finalCorrectCount = evaluations.stream().filter(IntentEvaluationService::isFinalCorrect).count();
        IntentEvaluationMetrics metrics = IntentEvaluationMetrics.builder()
                .candidateRecallAtK(ratio(candidateRecallCount, candidateExpectationCount))
                .top1Accuracy(ratio(top1CorrectCount, matchedExpectations.size()))
                .ambiguityRate(ratio(ambiguityCount, evaluations.size()))
                .rejectionRate(ratio(rejectionCount, evaluations.size()))
                .finalActionAccuracy(ratio(finalCorrectCount, evaluations.size()))
                .build();
        List<IntentEvaluationConfusionEntry> matrix = buildConfusionMatrix(evaluations);
        List<IntentEvaluationFailure> failures = evaluations.stream()
                .filter(item -> !isFinalCorrect(item))
                .map(item -> IntentEvaluationFailure.builder()
                        .sampleId(item.sample().getId())
                        .expectedAction(item.sample().getExpectedAction())
                        .actualAction(item.actualAction())
                        .expectedIntentCodes(item.sample().expectedIntentCodes())
                        .candidates(item.thresholdedCandidates())
                        .build())
                .toList();
        return IntentEvaluationReport.builder()
                .schemaVersion(IntentEvaluationDataset.SCHEMA_VERSION)
                .datasetVersion(dataset.getDatasetVersion())
                .generatedAt(Instant.now())
                .applicationId(dataset.getApplicationId())
                .applicationVersionId(dataset.getApplicationVersionId())
                .intentTreeId(dataset.getIntentTreeId())
                .intentTreeVersionId(dataset.getIntentTreeVersionId())
                .embeddingModelInstanceId(dataset.getEmbeddingModelInstanceId())
                .sampleCount(evaluations.size())
                .metrics(metrics)
                .confusionMatrix(matrix)
                .failures(failures)
                .build();
    }

    private static long recalledExpectedIntentCount(SampleEvaluation item) {
        Set<String> candidateCodes = item.retrievedCandidates().stream()
                .map(IntentEvaluationCandidate::intentCode)
                .collect(Collectors.toSet());
        return item.sample().expectedIntentCodes().stream().filter(candidateCodes::contains).count();
    }

    private static boolean isTop1Correct(SampleEvaluation item) {
        return !item.retrievedCandidates().isEmpty()
                && item.sample().expectedIntentCodes().contains(item.retrievedCandidates().getFirst().intentCode());
    }

    private static boolean isFinalCorrect(SampleEvaluation item) {
        if (item.sample().getExpectedAction() != item.actualAction()) {
            return false;
        }
        Set<String> actualCodes = item.thresholdedCandidates().stream()
                .map(IntentEvaluationCandidate::intentCode)
                .collect(Collectors.toSet());
        return switch (item.sample().getExpectedAction()) {
            case MATCHED -> item.thresholdedCandidates().size() == 1
                    && actualCodes.contains(item.sample().expectedIntentCodes().getFirst());
            case AMBIGUOUS -> actualCodes.equals(Set.copyOf(item.sample().expectedIntentCodes()));
            case REJECTED -> true;
        };
    }

    private static List<IntentEvaluationConfusionEntry> buildConfusionMatrix(List<SampleEvaluation> evaluations) {
        Map<IntentEvaluationExpectedAction, Map<IntentEvaluationExpectedAction, Long>> counts = new EnumMap<>(
                IntentEvaluationExpectedAction.class);
        for (SampleEvaluation evaluation : evaluations) {
            counts.computeIfAbsent(evaluation.sample().getExpectedAction(), ignored -> new EnumMap<>(
                            IntentEvaluationExpectedAction.class))
                    .merge(evaluation.actualAction(), 1L, Long::sum);
        }
        List<IntentEvaluationConfusionEntry> entries = new ArrayList<>();
        for (IntentEvaluationExpectedAction expected : IntentEvaluationExpectedAction.values()) {
            for (IntentEvaluationExpectedAction actual : IntentEvaluationExpectedAction.values()) {
                entries.add(new IntentEvaluationConfusionEntry(expected, actual,
                        counts.getOrDefault(expected, Map.of()).getOrDefault(actual, 0L)));
            }
        }
        return entries;
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0D : (double) numerator / denominator;
    }

    private static IntentEvaluationExpectedAction toExpectedAction(IntentCandidateSet candidateSet) {
        if (candidateSet.candidates().isEmpty()) {
            return IntentEvaluationExpectedAction.REJECTED;
        }
        return candidateSet.candidates().size() == 1
                ? IntentEvaluationExpectedAction.MATCHED
                : IntentEvaluationExpectedAction.AMBIGUOUS;
    }

    private static IntentEvaluationLeafMetadata toLeafMetadata(
            IntentNodeDefinition node,
            ApplicationIntentMapping mapping) {
        return IntentEvaluationLeafMetadata.builder()
                .intentCode(node.getCode())
                .name(node.getName())
                .description(node.getDescription())
                .positiveExamples(node.getPositiveExamples())
                .negativeExamples(node.getNegativeExamples())
                .targetType(mapping == null || mapping.getTargetType() == null ? null : mapping.getTargetType().name())
                .capabilityCode(mapping == null ? null : mapping.getCapabilityCode())
                .targetId(mapping == null ? null : mapping.getTargetId())
                .targetVersionId(mapping == null ? null : mapping.getTargetVersionId())
                .build();
    }

    private static void validateDataset(IntentEvaluationDataset dataset) {
        if (dataset == null || dataset.getSchemaVersion() != IntentEvaluationDataset.SCHEMA_VERSION) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "意图评测数据集协议版本不受支持");
        }
        requireText(dataset.getDatasetVersion(), "评测数据集datasetVersion不能为空");
        if (dataset.getApplicationId() == null || dataset.getApplicationVersionId() == null
                || dataset.getIntentTreeId() == null || dataset.getIntentTreeVersionId() == null
                || dataset.getEmbeddingModelInstanceId() == null) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "评测数据集缺少锁定版本信息");
        }
    }

    private static void validateSampleStructure(List<IntentEvaluationSample> samples) {
        if (samples.size() < MINIMUM_BASELINE_SAMPLE_COUNT) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "V1基线样本不得少于100条");
        }
        Set<String> ids = new HashSet<>();
        Set<IntentEvaluationScenario> scenarios = new HashSet<>();
        for (IntentEvaluationSample sample : samples) {
            if (sample == null || !ids.add(requireText(sample.getId(), "评测样本id不能为空"))) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "评测样本id重复");
            }
            requireText(sample.getQuery(), "评测样本query不能为空");
            if (sample.getScenario() == null) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "评测样本缺少scenario");
            }
            scenarios.add(sample.getScenario());
            if (sample.getExpectedAction() == null) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "评测样本缺少expectedAction");
            }
            List<String> expectedCodes = sample.expectedIntentCodes();
            if (sample.getExpectedAction() == IntentEvaluationExpectedAction.REJECTED && !expectedCodes.isEmpty()) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "拒识样本不能填写expectedIntentCodes");
            }
            if (sample.getExpectedAction() == IntentEvaluationExpectedAction.MATCHED && expectedCodes.size() != 1) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "唯一命中样本必须填写一个expectedIntentCode");
            }
            if (sample.getExpectedAction() == IntentEvaluationExpectedAction.AMBIGUOUS && expectedCodes.size() < 2) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "歧义样本至少需要两个expectedIntentCode");
            }
        }
        if (!scenarios.containsAll(Set.of(IntentEvaluationScenario.values()))) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "V1基线样本未覆盖全部规定场景");
        }
    }

    private static void validateExpectedIntentCodes(List<IntentEvaluationSample> samples, Set<String> leafCodes) {
        for (IntentEvaluationSample sample : samples) {
            if (!leafCodes.containsAll(sample.expectedIntentCodes())) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "评测样本包含当前发布意图树不存在的编码");
            }
        }
    }

    private static void validateVersionBinding(IntentEvaluationDataset dataset, ApplicationIntentBinding binding) {
        if (!Objects.equals(dataset.getIntentTreeId(), binding.getIntentTreeId())
                || !Objects.equals(dataset.getIntentTreeVersionId(), binding.getIntentTreeVersionId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "评测数据集与ApplicationVersion锁定的意图树版本不一致");
        }
    }

    private RuntimeApplicationVersionEntity loadApplicationVersion(Long applicationId, Long applicationVersionId) {
        return applicationRuntimeRepo.findVersion(applicationId, applicationVersionId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "锁定的ApplicationVersion不存在"));
    }

    private RuntimeIntentTreeVersionEntity loadIntentTreeVersion(Long intentTreeId, Long intentTreeVersionId) {
        return intentTreeRuntimeRepo.findVersion(intentTreeId, intentTreeVersionId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "锁定的IntentTreeVersion不存在"));
    }

    private static ApplicationIntentBinding requireBinding(ApplicationDefinition definition) {
        if (definition == null || definition.intentRouting() == null || !definition.intentRouting().isEnabled()
                || definition.intentRouting().getIntentTreeId() == null
                || definition.intentRouting().getIntentTreeVersionId() == null) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "ApplicationVersion没有启用且锁定意图路由");
        }
        return definition.intentRouting();
    }

    private static IntentTreeDefinition requireIntentTreeDefinition(RuntimeIntentTreeVersionEntity version) {
        if (version.getDefinitionSnapshot() == null) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "锁定的IntentTreeVersion缺少定义快照");
        }
        return version.getDefinitionSnapshot();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, message);
        }
        return value;
    }

    /**
     * 一条样本的内存评测结果。
     *
     * @param sample              原始样本
     * @param actualAction        实际动作
     * @param retrievedCandidates 原始 Top-K 候选
     * @param thresholdedCandidates 通过召回阈值的候选
     */
    private record SampleEvaluation(
            IntentEvaluationSample sample,
            IntentEvaluationExpectedAction actualAction,
            List<IntentEvaluationCandidate> retrievedCandidates,
            List<IntentEvaluationCandidate> thresholdedCandidates) {
    }
}

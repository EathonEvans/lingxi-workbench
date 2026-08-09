package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.common.enums.IntentRecognitionStatus;
import com.lingxi.workbench.application.runtime.model.IntentRecognitionCandidate;
import com.lingxi.workbench.application.runtime.model.IntentRecognitionRequest;
import com.lingxi.workbench.application.runtime.model.IntentRecognitionResult;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.application.runtime.repo.IntentTreeRuntimeRepo;
import com.lingxi.workbench.application.runtime.repo.IntentVectorRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.IntentRecognitionService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** 意图树向量召回和确定性分差门控实现。 */
@Service
@RequiredArgsConstructor
public class IntentRecognitionServiceImpl implements IntentRecognitionService {

    private final IntentTreeRuntimeRepo intentTreeRepo;
    private final IntentVectorRuntimeRepo vectorRepo;
    private final ModelRuntimeConfigService modelRuntimeConfigService;
    private final EmbeddingGateway embeddingGateway;

    @Override
    public IntentRecognitionResult recognize(IntentRecognitionRequest request) {
        requireRequest(request);
        RuntimeIntentTreeVersionEntity version = intentTreeRepo
                .findVersion(request.intentTreeId(), request.intentTreeVersionId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "锁定的意图树版本不存在"));
        IntentTreeDefinition definition = Objects.requireNonNull(
                version.getDefinitionSnapshot(), "意图树发布定义不能为空");
        EmbeddingModelRuntimeConfig modelConfig = modelRuntimeConfigService
                .getEmbeddingModelConfig(version.getEmbeddingModelInstanceId());
        List<float[]> vectors = embeddingGateway.embed(List.of(request.query().trim()), modelConfig);
        if (vectors.size() != 1 || Objects.isNull(vectors.getFirst()) || vectors.getFirst().length == 0) {
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "意图查询向量化失败");
        }

        IntentRoutingPolicy policy = definition.routingPolicy();
        List<IntentRecognitionCandidate> candidates = vectorRepo
                .search(vectors.getFirst(), version.getId(), version.getEmbeddingModelInstanceId(), policy.topK())
                .stream()
                .filter(candidate -> BigDecimal.valueOf(candidate.similarity())
                        .compareTo(policy.recallThreshold()) >= 0)
                .map(candidate -> new IntentRecognitionCandidate(candidate.intentCode(), candidate.similarity()))
                .toList();
        if (candidates.isEmpty()) {
            return new IntentRecognitionResult(IntentRecognitionStatus.NOT_MATCHED, List.of());
        }

        IntentRecognitionCandidate top = candidates.getFirst();
        List<IntentRecognitionCandidate> ambiguous = candidates.stream()
                .takeWhile(candidate -> BigDecimal.valueOf(top.confidence() - candidate.confidence())
                        .compareTo(policy.ambiguityMargin()) < 0)
                .toList();
        if (ambiguous.size() > 1) {
            return new IntentRecognitionResult(IntentRecognitionStatus.AMBIGUOUS, ambiguous);
        }
        return new IntentRecognitionResult(IntentRecognitionStatus.MATCHED, List.of(top));
    }

    private static void requireRequest(IntentRecognitionRequest request) {
        if (Objects.isNull(request) || Objects.isNull(request.intentTreeId())
                || Objects.isNull(request.intentTreeVersionId()) || Objects.isNull(request.query())
                || request.query().isBlank()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "意图识别请求不完整");
        }
    }
}

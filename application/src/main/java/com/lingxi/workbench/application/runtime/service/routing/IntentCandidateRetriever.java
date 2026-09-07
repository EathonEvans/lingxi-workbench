package com.lingxi.workbench.application.runtime.service.routing;

import com.lingxi.workbench.application.runtime.model.IntentCandidate;
import com.lingxi.workbench.application.runtime.model.IntentCandidateRequest;
import com.lingxi.workbench.application.runtime.model.IntentCandidateSet;
import com.lingxi.workbench.application.runtime.model.IntentCandidateSlot;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.application.runtime.repo.IntentTreeRuntimeRepo;
import com.lingxi.workbench.application.runtime.repo.IntentVectorRuntimeRepo;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import com.lingxi.workbench.workflow.model.intent.IntentNodeDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentNodeType;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentSlotDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

import static com.lingxi.workbench.workflow.common.constants.IntentRoutingConstants.INTENT_TREE_SCHEMA_VERSION;

/** 使用锁定的 Embedding 模型和向量索引召回候选，不产生最终意图动作。 */
@Service
@RequiredArgsConstructor
public class IntentCandidateRetriever {

    private static final int MAX_PARENT_DEPTH = 6;
    private static final int MAX_EXAMPLES_PER_KIND = 5;
    private static final int MAX_SLOTS = 20;
    private static final int MAX_METADATA_TEXT_LENGTH = 500;

    private final IntentTreeRuntimeRepo intentTreeRepo;
    private final IntentVectorRuntimeRepo vectorRepo;
    private final ModelRuntimeConfigService modelRuntimeConfigService;
    private final EmbeddingGateway embeddingGateway;

    public IntentCandidateSet retrieve(IntentCandidateRequest request) {
        requireRequest(request);
        RuntimeIntentTreeVersionEntity version = intentTreeRepo
                .findVersion(request.intentTreeId(), request.intentTreeVersionId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "锁定的意图树版本不存在"));
        IntentTreeDefinition definition = Objects.requireNonNull(
                version.getDefinitionSnapshot(), "意图树发布定义不能为空");
        if (definition.schemaVersion() != INTENT_TREE_SCHEMA_VERSION) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "意图树发布定义版本不受支持");
        }
        return retrieve(version, request.query());
    }

    /** 在线路由已经加载冻结版本，直接复用；按 ID 进入的评测与 Workflow 仍在入口解析版本。 */
    public IntentCandidateSet retrieve(RuntimeIntentTreeVersionEntity version, String query) {
        IntentTreeDefinition definition = version.getDefinitionSnapshot();
        EmbeddingModelRuntimeConfig modelConfig = modelRuntimeConfigService
                .getEmbeddingModelConfig(version.getEmbeddingModelInstanceId());
        List<float[]> vectors = embeddingGateway.embed(List.of(query.trim()), modelConfig);
        if (vectors.size() != 1 || Objects.isNull(vectors.getFirst()) || vectors.getFirst().length == 0) {
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "意图查询向量化失败");
        }

        IntentRoutingPolicy policy = Objects.requireNonNull(definition.routingPolicy(), "意图路由策略不能为空");
        Map<String, IntentNodeDefinition> nodesById = indexNodesById(definition.nodes());
        Map<String, IntentNodeDefinition> leavesByCode = indexLeavesByCode(definition.nodes());

        List<IntentVectorRuntimeRepo.IntentCandidateRow> rows = vectorRepo.search(
                vectors.getFirst(), version.getId(), version.getEmbeddingModelInstanceId(), policy.topK());
        List<IntentCandidate> retrievedCandidates = IntStream.range(0, rows.size())
                .mapToObj(index -> toCandidate(rows.get(index), index + 1, nodesById, leavesByCode))
                .toList();

        List<IntentCandidate> candidates = retrievedCandidates.stream()
                .filter(candidate -> BigDecimal.valueOf(candidate.similarity())
                        .compareTo(policy.recallThreshold()) >= 0)
                .toList();
        return new IntentCandidateSet(candidates, retrievedCandidates);
    }

    private static IntentCandidate toCandidate(
            IntentVectorRuntimeRepo.IntentCandidateRow row,
            int rank,
            Map<String, IntentNodeDefinition> nodesById,
            Map<String, IntentNodeDefinition> leavesByCode) {
        IntentNodeDefinition node = leavesByCode.get(row.intentCode());
        if (node == null || !Objects.equals(node.getId(), row.intentNodeId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "意图向量索引与发布定义不一致");
        }
        return IntentCandidate.builder()
                .intentCode(node.getCode())
                .name(boundedText(node.getName()))
                .parentPath(parentPath(node, nodesById))
                .description(boundedText(node.getDescription()))
                .positiveExamples(boundedTexts(node.getPositiveExamples(), MAX_EXAMPLES_PER_KIND))
                .negativeExamples(boundedTexts(node.getNegativeExamples(), MAX_EXAMPLES_PER_KIND))
                .slots(boundedSlots(node.getSlots()))
                .clarificationQuestion(boundedText(node.getClarificationQuestion()))
                .similarity(row.similarity())
                .rank(rank)
                .build();
    }

    private static Map<String, IntentNodeDefinition> indexNodesById(List<IntentNodeDefinition> nodes) {
        Map<String, IntentNodeDefinition> result = new LinkedHashMap<>();
        for (IntentNodeDefinition node : nodes == null ? List.<IntentNodeDefinition>of() : nodes) {
            if (node != null && node.getId() != null) {
                result.put(node.getId(), node);
            }
        }
        return result;
    }

    private static Map<String, IntentNodeDefinition> indexLeavesByCode(List<IntentNodeDefinition> nodes) {
        Map<String, IntentNodeDefinition> result = new LinkedHashMap<>();
        for (IntentNodeDefinition node : nodes == null ? List.<IntentNodeDefinition>of() : nodes) {
            if (node != null && node.isEnabled() && node.getNodeType() == IntentNodeType.LEAF
                    && node.getCode() != null) {
                result.put(node.getCode(), node);
            }
        }
        return result;
    }

    private static List<String> parentPath(
            IntentNodeDefinition leaf,
            Map<String, IntentNodeDefinition> nodesById) {
        List<String> path = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String parentId = leaf.getParentId();
        while (parentId != null && path.size() < MAX_PARENT_DEPTH && visited.add(parentId)) {
            IntentNodeDefinition parent = nodesById.get(parentId);
            if (parent == null) {
                break;
            }
            String name = boundedText(parent.getName());
            if (name != null) {
                path.add(name);
            }
            parentId = parent.getParentId();
        }
        Collections.reverse(path);
        return List.copyOf(path);
    }

    private static List<String> boundedTexts(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(IntentCandidateRetriever::boundedText)
                .filter(Objects::nonNull)
                .limit(limit)
                .toList();
    }

    private static List<IntentCandidateSlot> boundedSlots(List<IntentSlotDefinition> slots) {
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }
        return slots.stream()
                .filter(Objects::nonNull)
                .limit(MAX_SLOTS)
                .map(slot -> new IntentCandidateSlot(
                        boundedText(slot.getCode()),
                        boundedText(slot.getName()),
                        slot.getValueType(),
                        slot.isRequired()))
                .toList();
    }

    private static String boundedText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= MAX_METADATA_TEXT_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_METADATA_TEXT_LENGTH);
    }

    private static void requireRequest(IntentCandidateRequest request) {
        if (request == null || request.intentTreeId() == null || request.intentTreeVersionId() == null
                || request.query() == null || request.query().isBlank()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "意图候选召回请求不完整");
        }
    }
}

package com.lingxi.workbench.workflow.model;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Workflow 意图分类节点的强类型结果和模型调用事实。 */
@Getter
public class IntentClassifierNodeResult {

    private final IntentClassifierNodeStatus status;
    private final String intentCode;
    private final String reasonCode;
    private final Double similarity;
    private final List<String> candidateCodes;
    private final List<IntentClassifierRecognizedSlot> recognizedSlots;
    private final Long modelInstanceId;
    private final Long promptVersionId;
    private final TokenUsage tokenUsage;
    private final int modelCalls;
    private final int retryCount;
    private final long durationMillis;

    @Builder
    private IntentClassifierNodeResult(IntentClassifierNodeStatus status, String intentCode, String reasonCode,
                                       Double similarity, List<String> candidateCodes,
                                       List<IntentClassifierRecognizedSlot> recognizedSlots,
                                       Long modelInstanceId, Long promptVersionId, TokenUsage tokenUsage,
                                       int modelCalls, int retryCount, long durationMillis) {
        this.status = Objects.requireNonNull(status, "意图分类节点状态不能为空");
        if (Objects.isNull(reasonCode) || reasonCode.isBlank()) {
            throw new IllegalArgumentException("意图分类节点原因编码不能为空");
        }
        if (modelCalls < 0 || retryCount < 0 || durationMillis < 0) {
            throw new IllegalArgumentException("意图分类节点调用事实不能为负数");
        }
        this.intentCode = intentCode;
        this.reasonCode = reasonCode;
        this.similarity = similarity;
        this.candidateCodes = List.copyOf(Objects.requireNonNullElseGet(candidateCodes, List::of));
        this.recognizedSlots = List.copyOf(Objects.requireNonNullElseGet(recognizedSlots, List::of));
        this.modelInstanceId = modelInstanceId;
        this.promptVersionId = promptVersionId;
        this.tokenUsage = Objects.requireNonNullElseGet(tokenUsage, TokenUsage::empty);
        this.modelCalls = modelCalls;
        this.retryCount = retryCount;
        this.durationMillis = durationMillis;
    }

    /** 只把标准业务输出写入 Workflow 状态，不暴露模型和 Token 元数据。 */
    public Map<String, Object> standardOutputs() {
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("status", status.getCode());
        outputs.put("intentCode", intentCode);
        outputs.put("reasonCode", reasonCode);
        outputs.put("similarity", similarity);
        outputs.put("candidateCodes", candidateCodes);
        outputs.put("recognizedSlots", recognizedSlots);
        return Collections.unmodifiableMap(outputs);
    }
}

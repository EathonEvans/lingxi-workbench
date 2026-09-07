package com.lingxi.workbench.application.runtime.service.routing;

import com.lingxi.workbench.application.runtime.common.enums.IntentDecisionReason;
import com.lingxi.workbench.application.runtime.model.IntentCandidate;
import com.lingxi.workbench.application.runtime.model.IntentCandidateSlot;
import com.lingxi.workbench.application.runtime.model.IntentRecognizedSlot;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecision;
import com.lingxi.workbench.application.runtime.model.IntentSemanticSlotValue;
import com.lingxi.workbench.workflow.model.intent.IntentSlotValueType;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.ai.util.JacksonUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 意图模型输出的唯一可信出口：先严格校验 JSON 协议，再校验动作、候选和槽位语义。
 * 原生结构化输出与普通文本输出共用稳定 Schema，供应商约束不能替代服务端验收。
 */
final class IntentDecisionOutputProtocol {

    private static final int MAX_SLOT_STRING_CHARACTERS = 500;
    private static final String JSON_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "action": {"type": "string", "enum": ["SELECT", "CLARIFY", "DEFER"]},
                "selectedIntentCode": {"type": "string"},
                "alternativeIntentCodes": {"type": "array", "items": {"type": "string"}},
                "recognizedSlots": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "additionalProperties": false,
                    "properties": {
                      "slotCode": {"type": "string"},
                      "value": {
                        "type": "object",
                        "additionalProperties": false,
                        "properties": {
                          "stringValue": {"type": "string"},
                          "numberValue": {"type": "number"},
                          "dateValue": {"type": "string", "format": "date"},
                          "booleanValue": {"type": "boolean"}
                        }
                      }
                    },
                    "required": ["slotCode", "value"]
                  }
                },
                "reasonCode": {
                  "type": "string",
                  "enum": ["SINGLE_INTENT", "AMBIGUOUS_INTENT", "MISSING_REQUIRED_SLOT",
                    "OUT_OF_SCOPE", "MULTIPLE_INTENTS", "NEGATED_OR_CORRECTED",
                    "INSUFFICIENT_CONTEXT", "NO_SUITABLE_CANDIDATE"]
                }
              },
              "required": ["action", "reasonCode"]
            }
            """;

    private final JsonMapper jsonMapper = strictJsonMapper();
    private final Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(jsonMapper.readTree(JSON_SCHEMA));

    IntentSemanticDecision read(String source, List<IntentCandidate> candidates) {
        if (isBlank(source)) {
            throw invalid("模型未返回JSON决策对象");
        }
        IntentSemanticDecision decision;
        try {
            JsonNode output = jsonMapper.readTree(source);
            if (!schema.validate(output).isEmpty()) {
                throw invalid("输出字段、类型或枚举不符合JSON Schema");
            }
            decision = jsonMapper.treeToValue(output, IntentSemanticDecision.class);
        } catch (JacksonException exception) {
            // 解析器异常可能带有模型原文；修复提示及失败异常只保留平台定义的安全原因。
            throw invalid("输出不是合法JSON或包含无法解析的字段值");
        }
        validateDecision(decision, candidates);
        return decision;
    }

    String getFormat() {
        return "仅输出符合以下 JSON Schema 的 JSON 对象：\n" + JSON_SCHEMA;
    }

    String getJsonSchema() {
        return JSON_SCHEMA;
    }

    private static void validateDecision(IntentSemanticDecision decision, List<IntentCandidate> candidates) {
        // 必填字段、类型和平台保留枚举已由 Schema 拦截，这里只处理跨字段业务约束。
        Map<String, IntentCandidate> candidatesByCode = candidates.stream()
                .collect(Collectors.toMap(IntentCandidate::intentCode, Function.identity()));
        switch (decision.getAction()) {
            case SELECT -> validateSelect(decision, candidatesByCode);
            case CLARIFY -> validateClarify(decision, candidatesByCode);
            case DEFER -> validateDefer(decision);
        }
    }

    private static void validateSelect(IntentSemanticDecision decision, Map<String, IntentCandidate> candidatesByCode) {
        requireCandidate(decision.getSelectedIntentCode(), candidatesByCode);
        requireEmpty(decision.alternativeIntentCodes(), "SELECT不能包含alternativeIntentCodes");
        if (decision.getReasonCode() != IntentDecisionReason.SINGLE_INTENT) {
            throw invalid("SELECT只能使用SINGLE_INTENT原因");
        }
        validateSlots(decision.recognizedSlots(), candidatesByCode.get(decision.getSelectedIntentCode()));
    }

    private static void validateClarify(IntentSemanticDecision decision, Map<String, IntentCandidate> candidatesByCode) {
        if (isBlank(decision.getSelectedIntentCode())) {
            requireEmpty(decision.recognizedSlots(), "候选歧义澄清不能携带recognizedSlots");
            List<String> alternatives = decision.alternativeIntentCodes();
            if (alternatives.size() < 2) {
                throw invalid("候选歧义澄清至少需要两个alternativeIntentCodes");
            }
            requireUniqueCandidates(alternatives, candidatesByCode);
            if (decision.getReasonCode() != IntentDecisionReason.AMBIGUOUS_INTENT
                    && decision.getReasonCode() != IntentDecisionReason.INSUFFICIENT_CONTEXT) {
                throw invalid("候选歧义澄清原因不合法");
            }
            return;
        }
        requireCandidate(decision.getSelectedIntentCode(), candidatesByCode);
        requireEmpty(decision.alternativeIntentCodes(), "缺槽位澄清不能包含alternativeIntentCodes");
        if (decision.getReasonCode() != IntentDecisionReason.MISSING_REQUIRED_SLOT) {
            throw invalid("已选择候选的CLARIFY只能表示缺少必填槽位");
        }
        IntentCandidate candidate = candidatesByCode.get(decision.getSelectedIntentCode());
        validateSlots(decision.recognizedSlots(), candidate);
        Set<String> recognizedCodes = decision.recognizedSlots().stream()
                .map(IntentRecognizedSlot::getSlotCode)
                .collect(Collectors.toSet());
        boolean hasMissingRequiredSlot = candidate.getSlots().stream()
                .anyMatch(slot -> slot.required() && !recognizedCodes.contains(slot.code()));
        if (!hasMissingRequiredSlot) {
            throw invalid("MISSING_REQUIRED_SLOT必须仍有未识别的必填槽位");
        }
    }

    private static void validateDefer(IntentSemanticDecision decision) {
        if (!isBlank(decision.getSelectedIntentCode()) || !decision.alternativeIntentCodes().isEmpty()
                || !decision.recognizedSlots().isEmpty()) {
            throw invalid("DEFER不能携带候选编码或槽位值");
        }
        Set<IntentDecisionReason> allowed = Set.of(IntentDecisionReason.OUT_OF_SCOPE,
                IntentDecisionReason.MULTIPLE_INTENTS, IntentDecisionReason.NEGATED_OR_CORRECTED,
                IntentDecisionReason.INSUFFICIENT_CONTEXT, IntentDecisionReason.NO_SUITABLE_CANDIDATE);
        if (!allowed.contains(decision.getReasonCode())) {
            throw invalid("DEFER原因不合法");
        }
    }

    private static void validateSlots(List<IntentRecognizedSlot> recognizedSlots, IntentCandidate candidate) {
        Map<String, IntentCandidateSlot> slotsByCode = candidate.getSlots().stream()
                .collect(Collectors.toMap(IntentCandidateSlot::code, Function.identity()));
        Set<String> recognizedCodes = new HashSet<>();
        for (IntentRecognizedSlot recognizedSlot : recognizedSlots) {
            if (!recognizedCodes.add(recognizedSlot.getSlotCode())) {
                throw invalid("recognizedSlots包含重复槽位编码");
            }
            IntentCandidateSlot slot = slotsByCode.get(recognizedSlot.getSlotCode());
            if (Objects.isNull(slot)) {
                throw invalid("模型返回了候选未声明的槽位");
            }
            validateSlotValue(recognizedSlot.getValue(), slot.valueType());
        }
    }

    private static void validateSlotValue(IntentSemanticSlotValue value, IntentSlotValueType expectedType) {
        int populated = countNonNull(value.getStringValue(), value.getNumberValue(),
                value.getDateValue(), value.getBooleanValue());
        if (populated != 1) {
            throw invalid("槽位值必须且只能包含一个声明类型字段");
        }
        switch (expectedType) {
            case STRING -> {
                if (isBlank(value.getStringValue())
                        || value.getStringValue().length() > MAX_SLOT_STRING_CHARACTERS) {
                    throw invalid("STRING槽位值为空或超过长度上限");
                }
            }
            case NUMBER -> requireNonNull(value.getNumberValue(), "NUMBER槽位必须使用numberValue");
            case DATE -> requireNonNull(value.getDateValue(), "DATE槽位必须使用dateValue");
            case BOOLEAN -> requireNonNull(value.getBooleanValue(), "BOOLEAN槽位必须使用booleanValue");
        }
    }

    private static void requireUniqueCandidates(List<String> codes, Map<String, IntentCandidate> candidatesByCode) {
        if (new HashSet<>(codes).size() != codes.size()) {
            throw invalid("候选编码不能重复");
        }
        for (String code : codes) {
            requireCandidate(code, candidatesByCode);
        }
    }

    private static void requireCandidate(String code, Map<String, IntentCandidate> candidatesByCode) {
        if (isBlank(code) || !candidatesByCode.containsKey(code)) {
            throw invalid("模型选择了本次候选外编码");
        }
    }

    private static void requireEmpty(List<?> values, String message) {
        if (!values.isEmpty()) {
            throw invalid(message);
        }
    }

    private static void requireNonNull(Object value, String message) {
        if (Objects.isNull(value)) {
            throw invalid(message);
        }
    }

    private static int countNonNull(Object... values) {
        int count = 0;
        for (Object value : values) {
            if (Objects.nonNull(value)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isBlank(String value) {
        return Objects.isNull(value) || value.isBlank();
    }

    private static InvalidOutputException invalid(String message) {
        return new InvalidOutputException(message);
    }

    private static JsonMapper strictJsonMapper() {
        return JsonMapper.builder()
                .addModules(JacksonUtils.instantiateAvailableModules())
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    /** 仅承载平台定义的校验原因，调用方据此决定是否消耗修复额度。 */
    static final class InvalidOutputException extends RuntimeException {

        private InvalidOutputException(String message) {
            super(message);
        }
    }
}

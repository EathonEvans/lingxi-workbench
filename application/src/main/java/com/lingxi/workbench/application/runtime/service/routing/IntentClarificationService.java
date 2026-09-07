package com.lingxi.workbench.application.runtime.service.routing;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.application.runtime.common.enums.IntentClarificationType;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingReason;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingSessionStatus;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.IntentCandidate;
import com.lingxi.workbench.application.runtime.model.IntentClarificationContext;
import com.lingxi.workbench.application.runtime.model.IntentRecognizedSlot;
import com.lingxi.workbench.application.runtime.model.IntentRoutingEvidence;
import com.lingxi.workbench.application.runtime.model.IntentRoutingResult;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecision;
import com.lingxi.workbench.application.runtime.model.IntentSemanticSlotValue;
import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingSessionEntity;
import com.lingxi.workbench.application.runtime.repo.IntentRoutingSessionRepo;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.request.ClarificationResponseRequest;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentNodeDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentSlotDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentSlotValueType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 处理跨轮澄清的输入和状态推进；仅查询持久化状态，写入由路由提交事务负责。 */
@Service
@RequiredArgsConstructor
public class IntentClarificationService {
    private static final int MAX_CLARIFICATION_ROUNDS = 2;
    private static final int CLARIFICATION_TTL_MINUTES = 10;
    private static final Set<String> CANCEL_WORDS = Set.of("取消", "算了", "不用了", "退出");
    private static final DateTimeFormatter SLOT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-M-d");
    private final IntentRoutingSessionRepo sessionRepo;

    public Optional<IntentRoutingSessionEntity> findPending(IntentRoutingConfig configuration, ConversationContext context, ClarificationResponseRequest response) {
        AppRuntimeSnapshot application = configuration.getApplication();
        ChatTurnBO turn = context.getTurn();
        LoginUser loginUser = context.getLoginUser();
        if (Objects.nonNull(response) && CharSequenceUtil.isNotBlank(response.getClarificationId())) {
            long clarificationId;
            try {
                clarificationId = Long.parseLong(response.getClarificationId());
            } catch (NumberFormatException exception) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "澄清会话ID格式不正确");
            }
            IntentRoutingSessionEntity session = sessionRepo
                    .findOwned(clarificationId, turn.conversationId(), loginUser.id())
                    .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "澄清会话不存在"));
            // 防御：澄清会话必须属于当前 ApplicationVersion，防止跨版本串话
            if (!Objects.equals(session.getApplicationVersionId(), application.versionId())) {
                throw new BusinessException(HttpStatusEnum.CONFLICT, "澄清会话不属于当前Application版本");
            }
            // 状态必须是 PENDING，已结束的会话要求用户重新发起
            if (session.getStatus() != IntentRoutingSessionStatus.PENDING) {
                throw new BusinessException(HttpStatusEnum.CONFLICT, "澄清会话已经结束，请按当前需求重新发起");
            }
            requireTreeVersion(session, configuration);
            return Optional.of(session);
        }
        // 自动关联：查找同一对话中的最新 PENDING 会话
        Optional<IntentRoutingSessionEntity> pending = sessionRepo.findPending(turn.conversationId(), loginUser.id(), application.versionId());
        pending.ifPresent(session -> requireTreeVersion(session, configuration));
        return pending;
    }

    private static void requireTreeVersion(IntentRoutingSessionEntity session, IntentRoutingConfig configuration) {
        if (!Objects.equals(session.getIntentTreeVersionId(), configuration.getVersion().getId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "澄清会话不属于当前意图树版本");
        }
    }

    /** 首次识别与消歧成功共用槽位完整性规则；无缺失时不创建持久化会话。 */
    public IntentRoutingOutcome select(IntentRoutingConfig configuration, ConversationContext context,
                                       IntentSemanticDecision decision, IntentRoutingEvidence evidence) {
        IntentClarificationContext clarification = IntentClarificationContext.builder()
                .originalQuery(context.routingQuery()).intentCode(decision.getSelectedIntentCode())
                .collectedSlots(recognizedSlotValues(decision.recognizedSlots())).build();
        IntentRoutingSessionEntity session = newSession(context, configuration, IntentClarificationType.MISSING_SLOT, clarification);
        return continueSelected(session, configuration, evidence);
    }

    public IntentRoutingOutcome disambiguate(IntentRoutingConfig configuration, ConversationContext context,
                                             List<IntentCandidate> alternatives, IntentRoutingEvidence evidence) {
        List<String> codes = alternatives.stream().map(IntentCandidate::intentCode).toList();
        IntentClarificationContext clarification = IntentClarificationContext.builder().originalQuery(context.routingQuery())
                .candidateCodes(codes).collectedSlots(new LinkedHashMap<>()).build();
        IntentRoutingSessionEntity session = newSession(context, configuration, IntentClarificationType.INTENT_DISAMBIGUATION, clarification);
        return outcome(session, clarification(session, ambiguityQuestion(codes, configuration.getNodesByCode()),
                codes, configuration.getNodesByCode(), evidence));
    }

    public IntentRoutingOutcome resume(IntentRoutingConfig configuration, IntentRoutingSessionEntity session,
                                       String query, ClarificationResponseRequest response) {
        IntentRoutingOutcome.PendingState expected = new IntentRoutingOutcome.PendingState(session.getClarificationType(), session.getRoundNo());
        IntentRoutingOutcome outcome;
        String value = structuredValue(response, query);
        if (session.getExpiresAt().isBefore(OffsetDateTime.now())) {
            outcome = finish(session, IntentRoutingSessionStatus.EXPIRED, query, IntentRoutingReason.CLARIFICATION_EXPIRED);
        } else if (CANCEL_WORDS.contains(value.trim())) {
            outcome = finish(session, IntentRoutingSessionStatus.CANCELLED, query, IntentRoutingReason.CLARIFICATION_CANCELLED);
        } else if (session.getClarificationType() == IntentClarificationType.INTENT_DISAMBIGUATION) {
            String code = resolveCandidateCode(session.getContext().getCandidateCodes(), value, configuration.getNodesByCode());
            if (Objects.isNull(code)) {
                outcome = askAgain(session, configuration, null);
            } else {
                session.getContext().setIntentCode(code);
                outcome = continueSelected(session, configuration, reasonEvidence(IntentRoutingReason.CLARIFICATION_RESOLVED));
            }
        } else {
            outcome = collectSlot(session, value, query, configuration);
        }
        return outcome.toBuilder().expectedState(expected).build();
    }

    private IntentRoutingOutcome continueSelected(IntentRoutingSessionEntity session, IntentRoutingConfig configuration,
                                                  IntentRoutingEvidence evidence) {
        IntentClarificationContext context = session.getContext();
        IntentNodeDefinition node = configuration.getNodesByCode().get(context.getIntentCode());
        List<String> missing = missingSlots(node, context.getCollectedSlots());
        if (missing.isEmpty()) {
            session.setStatus(IntentRoutingSessionStatus.RESOLVED);
            IntentRoutingResult result = target(configuration, node.getCode(), enrichedQuery(context.getOriginalQuery(), context.getCollectedSlots()), evidence);
            return Objects.isNull(session.getId()) ? IntentRoutingOutcome.completed(result) : outcome(session, result);
        }
        // 消歧进入补槽位是一次状态变更，不先写 RESOLVED 再写 PENDING。
        context.setMissingSlotCodes(missing);
        session.setClarificationType(IntentClarificationType.MISSING_SLOT);
        session.setStatus(IntentRoutingSessionStatus.PENDING);
        session.setRoundNo(1);
        session.setExpiresAt(OffsetDateTime.now().plusMinutes(CLARIFICATION_TTL_MINUTES));
        return outcome(session, slotClarification(session, node, evidence));
    }

    private IntentRoutingOutcome collectSlot(IntentRoutingSessionEntity session, String value, String query,
                                             IntentRoutingConfig configuration) {
        IntentClarificationContext context = session.getContext();
        List<String> missing = new ArrayList<>(context.getMissingSlotCodes());
        if (missing.isEmpty()) {
            return finish(session, IntentRoutingSessionStatus.FAILED, query, IntentRoutingReason.CLARIFICATION_FAILED);
        }
        String code = missing.getFirst();
        IntentSlotDefinition slot = configuration.getNodesByCode().get(context.getIntentCode()).getSlots().stream()
                .filter(item -> item.getCode().equals(code)).findFirst().orElseThrow();
        String normalized = value.trim();
        if (!isValidSlotValue(slot, normalized)) {
            return askAgain(session, configuration, "「" + slot.getName() + "」格式不正确，请按" + slot.getValueType().getDescribe() + "格式重新输入。");
        }
        Map<String, String> collected = new LinkedHashMap<>(context.getCollectedSlots());
        collected.put(code, normalized);
        context.setCollectedSlots(collected);
        missing.removeFirst();
        context.setMissingSlotCodes(missing);
        if (!missing.isEmpty()) return askAgain(session, configuration, null);
        session.setStatus(IntentRoutingSessionStatus.COMPLETED);
        return outcome(session, target(configuration, context.getIntentCode(), enrichedQuery(context.getOriginalQuery(), context.getCollectedSlots()),
                reasonEvidence(IntentRoutingReason.CLARIFICATION_RESOLVED)));
    }

    /** 轮次、有效期和超限结束只有一个维护位置，格式错误与普通追问共用。 */
    private IntentRoutingOutcome askAgain(IntentRoutingSessionEntity session, IntentRoutingConfig configuration, String invalidQuestion) {
        if (session.getRoundNo() >= MAX_CLARIFICATION_ROUNDS) {
            return finish(session, IntentRoutingSessionStatus.FAILED, session.getContext().getOriginalQuery(), IntentRoutingReason.CLARIFICATION_FAILED);
        }
        session.setRoundNo(session.getRoundNo() + 1);
        session.setExpiresAt(OffsetDateTime.now().plusMinutes(CLARIFICATION_TTL_MINUTES));
        IntentRoutingEvidence evidence = reasonEvidence(IntentRoutingReason.CLARIFICATION_RETRY);
        IntentRoutingResult result;
        if (Objects.nonNull(invalidQuestion)) {
            result = clarification(session, invalidQuestion, List.of(), Map.of(), evidence);
        } else if (session.getClarificationType() == IntentClarificationType.INTENT_DISAMBIGUATION) {
            List<String> codes = session.getContext().getCandidateCodes();
            result = clarification(session, ambiguityQuestion(codes, configuration.getNodesByCode()), codes, configuration.getNodesByCode(), evidence);
        } else {
            result = slotClarification(session, configuration.getNodesByCode().get(session.getContext().getIntentCode()), evidence);
        }
        return outcome(session, result);
    }

    private static IntentRoutingOutcome finish(IntentRoutingSessionEntity session, IntentRoutingSessionStatus status, String query, IntentRoutingReason reason) {
        session.setStatus(status);
        return outcome(session, IntentRoutingResult.deferUnmatched(query, reason));
    }

    private static IntentRoutingOutcome outcome(IntentRoutingSessionEntity session, IntentRoutingResult result) {
        return IntentRoutingOutcome.builder().session(session).result(result).build();
    }

    private static IntentRoutingSessionEntity newSession(ConversationContext turn, IntentRoutingConfig configuration,
                                                         IntentClarificationType type, IntentClarificationContext context) {
        IntentRoutingSessionEntity session = new IntentRoutingSessionEntity();
        session.setConversationId(turn.getTurn().conversationId());
        session.setUserId(turn.getLoginUser().id());
        session.setApplicationVersionId(configuration.getApplication().versionId());
        session.setIntentTreeVersionId(configuration.getVersion().getId());
        session.setStatus(IntentRoutingSessionStatus.PENDING);
        session.setClarificationType(type);
        session.setRoundNo(1);
        session.setContext(context);
        session.setExpiresAt(OffsetDateTime.now().plusMinutes(CLARIFICATION_TTL_MINUTES));
        return session;
    }

    private static List<String> missingSlots(IntentNodeDefinition node, Map<String, String> collectedSlots) {
        return requiredSlots(node).stream()
                .filter(slotCode -> !collectedSlots.containsKey(slotCode))
                .toList();
    }

    private static Map<String, String> recognizedSlotValues(List<IntentRecognizedSlot> recognizedSlots) {
        Map<String, String> values = new LinkedHashMap<>();
        for (IntentRecognizedSlot slot : recognizedSlots) {
            values.put(slot.getSlotCode(), semanticSlotValue(slot.getValue()));
        }
        return values;
    }

    private static String semanticSlotValue(IntentSemanticSlotValue value) {
        if (Objects.nonNull(value.getStringValue())) {
            return value.getStringValue();
        }
        if (Objects.nonNull(value.getNumberValue())) {
            return value.getNumberValue().toPlainString();
        }
        if (Objects.nonNull(value.getDateValue())) {
            return value.getDateValue().toString();
        }
        return String.valueOf(value.getBooleanValue());
    }

    private static boolean isValidSlotValue(IntentSlotDefinition slot, String value) {
        IntentSlotValueType type = slot.getValueType();
        return switch (type) {
            case STRING -> true;
            case NUMBER -> isNumber(value);
            case DATE -> isDate(value);
            case BOOLEAN -> isBoolean(value);
        };
    }

    private static boolean isNumber(String value) {
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static boolean isDate(String value) {
        try {
            // 将 / 分隔符归一为 -，yyyy-M-d 允许月日不补零
            LocalDate.parse(value.trim().replace('/', '-'), SLOT_DATE_FORMAT);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private static boolean isBoolean(String value) {
        return Set.of("true", "false", "是", "否", "1", "0", "开", "关")
                .contains(value.toLowerCase(Locale.ROOT));
    }

    private static IntentRoutingResult target(IntentRoutingConfig configuration, String intentCode, String query,
                                              IntentRoutingEvidence evidence) {
        Optional<ApplicationIntentMapping> resolvedMapping = configuration.findMapping(intentCode);
        if (resolvedMapping.isEmpty()) {
            return IntentRoutingResult.deferUnmatched(query, IntentRoutingReason.UNMAPPED_CANDIDATE);
        }
        ApplicationIntentMapping mapping = resolvedMapping.get();
        IntentRoutingAction action = switch (mapping.getTargetType()) {
            case WORKFLOW -> IntentRoutingAction.EXECUTE_WORKFLOW;
            case AGENT -> IntentRoutingAction.EXECUTE_AGENT;
            case FIXED_RESPONSE -> IntentRoutingAction.FIXED_RESPONSE;
        };
        return IntentRoutingResult.builder()
                .action(action)
                .intentCode(intentCode)
                .mapping(mapping)
                .executionQuery(query)
                .response(mapping.getFixedResponse())
                .routingEvidence(evidence)
                .build();
    }

    private static IntentRoutingEvidence reasonEvidence(IntentRoutingReason reason) {
        return IntentRoutingEvidence.builder()
                .reason(reason)
                .candidates(List.of())
                .build();
    }

    private static IntentRoutingResult clarification(IntentRoutingSessionEntity session, String question, List<String> optionCodes, Map<String, IntentNodeDefinition> nodesByCode,
                                                     IntentRoutingEvidence evidence) {
        List<ChatClarificationEventBO.ClarificationOptionBO> options = optionCodes.stream()
                .map(code -> new ChatClarificationEventBO.ClarificationOptionBO(code, Optional.ofNullable(nodesByCode.get(code))
                        .map(IntentNodeDefinition::getName)
                        .orElse(code)))
                .toList();
        ChatClarificationEventBO event = ChatClarificationEventBO.builder()
                .clarificationId(null)
                .question(question)
                .options(options)
                .round(session.getRoundNo())
                .expiresAt(session.getExpiresAt())
                .build();
        return IntentRoutingResult.builder()
                .action(IntentRoutingAction.CLARIFICATION)
                .response(question)
                .clarification(event)
                .routingEvidence(evidence)
                .build();
    }

    private static IntentRoutingResult slotClarification(IntentRoutingSessionEntity session, IntentNodeDefinition node,
                                                         IntentRoutingEvidence evidence) {
        String slotCode = session.getContext().getMissingSlotCodes().getFirst();
        String slotName = Optional.ofNullable(node.getSlots()).orElse(List.of()).stream()
                .filter(slot -> Objects.equals(slot.getCode(), slotCode))
                .map(IntentSlotDefinition::getName)
                .findFirst()
                .orElse(slotCode);
        String question = CharSequenceUtil.isNotBlank(node.getClarificationQuestion())
                ? node.getClarificationQuestion().trim()
                : "请补充\u201C" + slotName + "\u201D。";
        return clarification(session, question, List.of(), Map.of(), evidence).toBuilder().intentCode(node.getCode()).build();
    }

    private static String resolveCandidateCode(List<String> candidateCodes, String value, Map<String, IntentNodeDefinition> nodesByCode) {
        String normalized = value.trim();
        for (int index = 0; index < candidateCodes.size(); index++) {
            String code = candidateCodes.get(index);
            IntentNodeDefinition node = nodesByCode.get(code);
            if (normalized.equalsIgnoreCase(code)
                    || (Objects.nonNull(node) && normalized.equals(node.getName()))
                    || ordinalMatches(normalized, index)) {
                return code;
            }
        }
        return null;
    }

    private static boolean ordinalMatches(String value, int index) {
        return switch (index) {
            case 0 -> Set.of("1", "一", "第一个", "第一项").contains(value);
            case 1 -> Set.of("2", "二", "第二个", "第二项").contains(value);
            case 2 -> Set.of("3", "三", "第三个", "第三项").contains(value);
            default -> value.equals(String.valueOf(index + 1));
        };
    }

    private static List<String> requiredSlots(IntentNodeDefinition node) {
        return Optional.ofNullable(node.getSlots()).orElse(List.of()).stream()
                .filter(IntentSlotDefinition::isRequired)
                .map(IntentSlotDefinition::getCode)
                .toList();
    }

    private static String ambiguityQuestion(List<String> codes, Map<String, IntentNodeDefinition> nodesByCode) {
        String options = java.util.stream.IntStream.range(0, codes.size())
                .mapToObj(index -> (index + 1) + ". " + nodesByCode.get(codes.get(index)).getName())
                .collect(Collectors.joining("；"));
        return "我还不能确定你的具体意图，请选择：" + options;
    }

    private static String structuredValue(ClarificationResponseRequest response, String query) {
        if (Objects.isNull(response)) {
            return query;
        }
        if (CharSequenceUtil.isNotBlank(response.getOptionCode())) {
            return response.getOptionCode();
        }
        return CharSequenceUtil.isNotBlank(response.getValue()) ? response.getValue() : query;
    }

    private static String enrichedQuery(String query, Map<String, String> collectedSlots) {
        if (collectedSlots.isEmpty()) {
            return query;
        }
        String slots = collectedSlots.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("，"));
        return query + "\n已确认参数：" + slots;
    }

}

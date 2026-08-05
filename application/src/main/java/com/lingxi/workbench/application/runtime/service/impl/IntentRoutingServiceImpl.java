package com.lingxi.workbench.application.runtime.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.application.runtime.common.enums.IntentClarificationType;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingSessionStatus;
import com.lingxi.workbench.application.runtime.model.ApplicationRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.IntentClarificationContext;
import com.lingxi.workbench.application.runtime.model.IntentRoutingResult;
import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingSessionEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.application.runtime.repo.IntentRoutingSessionRepo;
import com.lingxi.workbench.application.runtime.repo.IntentTreeRuntimeRepo;
import com.lingxi.workbench.application.runtime.repo.IntentVectorRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.IntentRoutingService;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.request.ClarificationResponseRequest;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import com.lingxi.workbench.workflow.model.intent.IntentFallbackPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentNodeDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentSlotDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 基于发布向量、确定性分差门控和持久化澄清会话的 V1 意图路由。 */
@Service
@RequiredArgsConstructor
public class IntentRoutingServiceImpl implements IntentRoutingService {

    private static final int MAX_CLARIFICATION_ROUNDS = 2;
    private static final int CLARIFICATION_TTL_MINUTES = 10;
    private static final Set<String> CANCEL_WORDS = Set.of("取消", "算了", "不用了", "退出");

    private final IntentTreeRuntimeRepo intentTreeRepo;
    private final IntentVectorRuntimeRepo vectorRepo;
    private final IntentRoutingSessionRepo sessionRepo;
    private final ModelRuntimeConfigService modelRuntimeConfigService;
    private final EmbeddingGateway embeddingGateway;

    @Override
    public IntentRoutingResult route(
            ApplicationRuntimeSnapshot application, String query, LoginUser loginUser,
            ChatTurnBO turn, ClarificationResponseRequest clarificationResponse) {


        ApplicationIntentBinding binding = application.definition().intentBinding();
        // 未绑定意图书或者未开启意图
        if (Objects.isNull(binding) || !binding.isEnabled()) {
            return IntentRoutingResult.workflow(null, query);
        }
        RuntimeIntentTreeVersionEntity version = intentTreeRepo
                .findVersion(binding.getIntentTreeId(), binding.getIntentTreeVersionId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "Application锁定的意图树版本不存在"));
        IntentTreeDefinition definition = Objects.requireNonNull(
                version.getDefinitionSnapshot(), "意图树发布定义不能为空");
        Map<String, IntentNodeDefinition> nodesByCode = definition.nodes().stream()
                .collect(Collectors.toMap(IntentNodeDefinition::getCode, Function.identity()));

        Optional<IntentRoutingSessionEntity> pending = findPending(
                application,
                turn,
                loginUser,
                clarificationResponse);
        if (pending.isPresent()) {
            IntentRoutingResult resumed = resumeClarification(
                    pending.get(),
                    query,
                    clarificationResponse,
                    binding,
                    nodesByCode);
            if (Objects.nonNull(resumed)) {
                return resumed;
            }
        }
        return routeFresh(application, query, loginUser, turn, binding, version, definition, nodesByCode);
    }

    private Optional<IntentRoutingSessionEntity> findPending(
            ApplicationRuntimeSnapshot application,
            ChatTurnBO turn,
            LoginUser loginUser,
            ClarificationResponseRequest response) {
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
            if (!Objects.equals(session.getApplicationVersionId(), application.applicationVersionId())) {
                throw new BusinessException(HttpStatusEnum.CONFLICT, "澄清会话不属于当前Application版本");
            }
            if (session.getStatus() != IntentRoutingSessionStatus.PENDING) {
                throw new BusinessException(HttpStatusEnum.CONFLICT, "澄清会话已经结束，请按当前需求重新发起");
            }
            return Optional.of(session);
        }
        return sessionRepo.findPending(
                turn.conversationId(), loginUser.id(), application.applicationVersionId());
    }

    /** 返回 null 表示旧会话已取消或过期，本条消息应作为新任务重新识别。 */
    private IntentRoutingResult resumeClarification(
            IntentRoutingSessionEntity session,
            String query,
            ClarificationResponseRequest response,
            ApplicationIntentBinding binding,
            Map<String, IntentNodeDefinition> nodesByCode) {
        if (session.getExpiresAt().isBefore(OffsetDateTime.now())) {
            session.setStatus(IntentRoutingSessionStatus.EXPIRED);
            sessionRepo.update(session);
            return null;
        }
        String suppliedValue = structuredValue(response, query);
        if (CANCEL_WORDS.contains(suppliedValue.trim())) {
            session.setStatus(IntentRoutingSessionStatus.CANCELLED);
            sessionRepo.update(session);
            return fixed(fallback(binding).clarificationFailureResponse());
        }
        if (session.getClarificationType() == IntentClarificationType.INTENT_DISAMBIGUATION) {
            String intentCode = resolveCandidateCode(
                    session.getContext().getCandidateCodes(), suppliedValue, nodesByCode);
            if (Objects.isNull(intentCode)) {
                return repeatOrFail(session, binding, nodesByCode);
            }
            session.getContext().setIntentCode(intentCode);
            session.setStatus(IntentRoutingSessionStatus.RESOLVED);
            sessionRepo.update(session);
            return resolveSlotsOrTarget(
                    session,
                    originalQuery(session.getContext(), query),
                    binding,
                    nodesByCode.get(intentCode));
        }
        return collectSlot(session, suppliedValue, query, binding, nodesByCode);
    }

    private IntentRoutingResult routeFresh(
            ApplicationRuntimeSnapshot application,
            String query,
            LoginUser loginUser,
            ChatTurnBO turn,
            ApplicationIntentBinding binding,
            RuntimeIntentTreeVersionEntity version,
            IntentTreeDefinition definition,
            Map<String, IntentNodeDefinition> nodesByCode) {
        EmbeddingModelRuntimeConfig config = modelRuntimeConfigService
                .getEmbeddingModelConfig(version.getEmbeddingModelInstanceId());
        List<float[]> embedded = embeddingGateway.embed(List.of(query), config);
        if (embedded.size() != 1 || embedded.getFirst() == null || embedded.getFirst().length == 0) {
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "意图查询向量化失败");
        }
        IntentRoutingPolicy policy = definition.routingPolicy();
        List<IntentVectorRuntimeRepo.IntentCandidateRow> candidates = vectorRepo
                .search(embedded.getFirst(), version.getId(), version.getEmbeddingModelInstanceId(), policy.topK())
                .stream()
                .filter(candidate -> BigDecimal.valueOf(candidate.similarity())
                        .compareTo(policy.recallThreshold()) >= 0)
                .toList();
        if (candidates.isEmpty()) {
            return fixed(fallback(binding).unmatchedResponse());
        }
        if (candidates.size() > 1
                && BigDecimal.valueOf(candidates.get(0).similarity() - candidates.get(1).similarity())
                        .compareTo(policy.ambiguityMargin()) < 0) {
            List<String> candidateCodes = candidates.stream()
                    .map(IntentVectorRuntimeRepo.IntentCandidateRow::intentCode)
                    .toList();
            IntentRoutingSessionEntity session = createSession(
                    turn,
                    loginUser,
                    application,
                    version.getId(),
                    IntentClarificationType.INTENT_DISAMBIGUATION,
                    IntentClarificationContext.builder()
                            .originalQuery(query)
                            .candidateCodes(candidateCodes)
                            .collectedSlots(new LinkedHashMap<>())
                            .build());
            return clarification(session, ambiguityQuestion(candidateCodes, nodesByCode), candidateCodes, nodesByCode);
        }
        String intentCode = candidates.getFirst().intentCode();
        IntentRoutingSessionEntity transientSession = sessionShell(
                turn, loginUser, application, version.getId(), intentCode, query);
        return resolveSlotsOrTarget(transientSession, query, binding, nodesByCode.get(intentCode));
    }

    private IntentRoutingResult resolveSlotsOrTarget(
            IntentRoutingSessionEntity session,
            String query,
            ApplicationIntentBinding binding,
            IntentNodeDefinition node) {
        List<String> requiredSlots = requiredSlots(node);
        if (!requiredSlots.isEmpty()) {
            IntentClarificationContext context = session.getContext();
            if (CharSequenceUtil.isBlank(context.getOriginalQuery())) {
                context.setOriginalQuery(query);
            }
            context.setIntentCode(node.getCode());
            context.setMissingSlotCodes(requiredSlots);
            context.setCollectedSlots(Objects.requireNonNullElseGet(
                    context.getCollectedSlots(), LinkedHashMap::new));
            session.setClarificationType(IntentClarificationType.MISSING_SLOT);
            session.setStatus(IntentRoutingSessionStatus.PENDING);
            session.setRoundNo(1);
            session.setExpiresAt(OffsetDateTime.now().plusMinutes(CLARIFICATION_TTL_MINUTES));
            if (Objects.isNull(session.getId())) {
                sessionRepo.insert(session);
            } else {
                sessionRepo.update(session);
            }
            return slotClarification(session, node);
        }
        return target(binding, node.getCode(), query);
    }

    private IntentRoutingResult collectSlot(
            IntentRoutingSessionEntity session,
            String value,
            String originalQuery,
            ApplicationIntentBinding binding,
            Map<String, IntentNodeDefinition> nodesByCode) {
        IntentClarificationContext context = session.getContext();
        List<String> missing = new ArrayList<>(context.getMissingSlotCodes());
        if (missing.isEmpty()) {
            session.setStatus(IntentRoutingSessionStatus.FAILED);
            sessionRepo.update(session);
            return fixed(fallback(binding).clarificationFailureResponse());
        }
        String slotCode = missing.removeFirst();
        Map<String, String> collected = new LinkedHashMap<>(
                Objects.requireNonNullElse(context.getCollectedSlots(), Map.of()));
        collected.put(slotCode, value.trim());
        context.setCollectedSlots(collected);
        context.setMissingSlotCodes(missing);
        if (!missing.isEmpty()) {
            if (session.getRoundNo() >= MAX_CLARIFICATION_ROUNDS) {
                session.setStatus(IntentRoutingSessionStatus.FAILED);
                sessionRepo.update(session);
                return fixed(fallback(binding).clarificationFailureResponse());
            }
            session.setRoundNo(session.getRoundNo() + 1);
            session.setExpiresAt(OffsetDateTime.now().plusMinutes(CLARIFICATION_TTL_MINUTES));
            sessionRepo.update(session);
            return slotClarification(session, nodesByCode.get(context.getIntentCode()));
        }
        session.setStatus(IntentRoutingSessionStatus.COMPLETED);
        sessionRepo.update(session);
        return target(
                binding,
                context.getIntentCode(),
                enrichedQuery(originalQuery(context, originalQuery), collected));
    }

    private IntentRoutingResult repeatOrFail(
            IntentRoutingSessionEntity session,
            ApplicationIntentBinding binding,
            Map<String, IntentNodeDefinition> nodesByCode) {
        if (session.getRoundNo() >= MAX_CLARIFICATION_ROUNDS) {
            session.setStatus(IntentRoutingSessionStatus.FAILED);
            sessionRepo.update(session);
            return fixed(fallback(binding).clarificationFailureResponse());
        }
        session.setRoundNo(session.getRoundNo() + 1);
        session.setExpiresAt(OffsetDateTime.now().plusMinutes(CLARIFICATION_TTL_MINUTES));
        sessionRepo.update(session);
        List<String> codes = session.getContext().getCandidateCodes();
        return clarification(session, ambiguityQuestion(codes, nodesByCode), codes, nodesByCode);
    }

    private IntentRoutingSessionEntity createSession(
            ChatTurnBO turn,
            LoginUser loginUser,
            ApplicationRuntimeSnapshot application,
            Long intentTreeVersionId,
            IntentClarificationType type,
            IntentClarificationContext context) {
        IntentRoutingSessionEntity session = new IntentRoutingSessionEntity();
        session.setConversationId(turn.conversationId());
        session.setUserId(loginUser.id());
        session.setApplicationVersionId(application.applicationVersionId());
        session.setIntentTreeVersionId(intentTreeVersionId);
        session.setStatus(IntentRoutingSessionStatus.PENDING);
        session.setClarificationType(type);
        session.setRoundNo(1);
        session.setContext(context);
        session.setExpiresAt(OffsetDateTime.now().plusMinutes(CLARIFICATION_TTL_MINUTES));
        sessionRepo.insert(session);
        return session;
    }

    private static IntentRoutingSessionEntity sessionShell(
            ChatTurnBO turn,
            LoginUser loginUser,
            ApplicationRuntimeSnapshot application,
            Long intentTreeVersionId,
            String intentCode,
            String originalQuery) {
        IntentRoutingSessionEntity session = new IntentRoutingSessionEntity();
        session.setConversationId(turn.conversationId());
        session.setUserId(loginUser.id());
        session.setApplicationVersionId(application.applicationVersionId());
        session.setIntentTreeVersionId(intentTreeVersionId);
        session.setContext(IntentClarificationContext.builder()
                .originalQuery(originalQuery)
                .intentCode(intentCode)
                .collectedSlots(new LinkedHashMap<>())
                .build());
        return session;
    }

    private static IntentRoutingResult target(
            ApplicationIntentBinding binding,
            String intentCode,
            String query) {
        ApplicationIntentMapping mapping = binding.mappings().stream()
                .filter(item -> Objects.equals(item.getIntentCode(), intentCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "意图缺少已发布执行映射：" + intentCode));
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
                .build();
    }

    private static IntentRoutingResult fixed(String response) {
        return IntentRoutingResult.builder()
                .action(IntentRoutingAction.FIXED_RESPONSE)
                .response(response)
                .build();
    }

    private static IntentRoutingResult clarification(
            IntentRoutingSessionEntity session,
            String question,
            List<String> optionCodes,
            Map<String, IntentNodeDefinition> nodesByCode) {
        List<ChatClarificationEventBO.ClarificationOptionBO> options = optionCodes.stream()
                .map(code -> new ChatClarificationEventBO.ClarificationOptionBO(
                        code,
                        Optional.ofNullable(nodesByCode.get(code))
                                .map(IntentNodeDefinition::getName)
                                .orElse(code)))
                .toList();
        ChatClarificationEventBO event = ChatClarificationEventBO.builder()
                .clarificationId(String.valueOf(session.getId()))
                .question(question)
                .options(options)
                .round(session.getRoundNo())
                .expiresAt(session.getExpiresAt())
                .build();
        return IntentRoutingResult.builder()
                .action(IntentRoutingAction.CLARIFICATION)
                .response(question)
                .clarification(event)
                .build();
    }

    private static IntentRoutingResult slotClarification(
            IntentRoutingSessionEntity session,
            IntentNodeDefinition node) {
        String slotCode = session.getContext().getMissingSlotCodes().getFirst();
        String slotName = Optional.ofNullable(node.getSlots()).orElse(List.of()).stream()
                .filter(slot -> Objects.equals(slot.getCode(), slotCode))
                .map(IntentSlotDefinition::getName)
                .findFirst()
                .orElse(slotCode);
        String question = CharSequenceUtil.isNotBlank(node.getClarificationQuestion())
                ? node.getClarificationQuestion().trim()
                : "请补充“" + slotName + "”。";
        return clarification(session, question, List.of(), Map.of());
    }

    private static String resolveCandidateCode(
            List<String> candidateCodes,
            String value,
            Map<String, IntentNodeDefinition> nodesByCode) {
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

    private static String ambiguityQuestion(
            List<String> codes,
            Map<String, IntentNodeDefinition> nodesByCode) {
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

    private static String originalQuery(IntentClarificationContext context, String fallbackQuery) {
        return CharSequenceUtil.isNotBlank(context.getOriginalQuery())
                ? context.getOriginalQuery()
                : fallbackQuery;
    }

    private static IntentFallbackPolicy fallback(ApplicationIntentBinding binding) {
        return Objects.requireNonNull(binding.getFallbackPolicy(), "意图兜底策略不能为空");
    }
}

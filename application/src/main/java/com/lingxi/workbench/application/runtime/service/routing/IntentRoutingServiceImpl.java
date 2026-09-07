package com.lingxi.workbench.application.runtime.service.routing;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.application.runtime.common.enums.IntentClarificationType;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingReason;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingSessionStatus;
import com.lingxi.workbench.application.runtime.common.enums.IntentDecisionReason;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.IntentClarificationContext;
import com.lingxi.workbench.application.runtime.model.IntentCandidate;
import com.lingxi.workbench.application.runtime.model.IntentCandidateRequest;
import com.lingxi.workbench.application.runtime.model.IntentCandidateSet;
import com.lingxi.workbench.application.runtime.model.IntentRoutingEvidence;
import com.lingxi.workbench.application.runtime.model.IntentRoutingEvidenceCandidate;
import com.lingxi.workbench.application.runtime.model.IntentRoutingDecisionCandidate;
import com.lingxi.workbench.application.runtime.model.IntentRoutingResult;
import com.lingxi.workbench.application.runtime.model.IntentRecognizedSlot;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecision;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecisionResult;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecisionSnapshot;
import com.lingxi.workbench.application.runtime.model.IntentSemanticSlotValue;
import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingDecisionEntity;
import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingSessionEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.application.runtime.repo.IntentRoutingSessionRepo;
import com.lingxi.workbench.application.runtime.repo.IntentTreeRuntimeRepo;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.request.ClarificationResponseRequest;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.CapabilityDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentNodeDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentSlotDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentSlotValueType;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import lombok.Builder;
import lombok.Getter;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.lingxi.workbench.workflow.common.constants.IntentRoutingConstants.INTENT_TREE_SCHEMA_VERSION;

/**
 * 基于发布候选召回和持久化澄清会话的意图路由实现。
 *
 * <h2>路由入口</h2>
 * <p>{@link #route(ConversationContext, ClarificationResponseRequest)}
 * 是唯一公开入口，由 {@code AppRuntime} 在
 * {@code ChatStreamServiceImpl.startChat()} 中调用。</p>
 *
 * <h2>执行流程</h2>
 * <ol>
 *   <li><b>前置检查</b>：Application 是否启用意图路由？未启用则交回 fallback Agent。</li>
 *   <li><b>加载版本快照</b>：读取 ApplicationVersion 中锁定的 IntentTreeVersion，
 *       解析 IntentTreeDefinition 并构建 code→node 索引。</li>
 *   <li><b>检查待处理澄清</b>：同一对话中是否有 PENDING 状态的澄清会话？
 *       有 → 恢复澄清流程，处理用户的选择或槽位输入。
 *       如会话已过期或取消 → 返回 null，重新识别。</li>
 *   <li><b>新请求识别</b>：向量化用户输入 → pgvector 余弦召回 Top-K 候选
 *       → 过滤低于阈值的候选 → 锁定模型在候选范围内作强类型语义决策。</li>
 *   <li><b>结果分发</b>：
 *       <ul>
 *         <li>无候选 → 返回未命中兜底文案</li>
 *         <li>CLARIFY → 创建候选消歧或缺槽位澄清会话</li>
 *         <li>SELECT → 校验并合并已识别槽位，槽位完整后返回锁定执行目标</li>
 *         <li>DEFER → 交给 Application fallback Agent</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h2>关键设计决策</h2>
 * <ul>
 *   <li><b>发布版本锁定</b>：意图树、映射和能力版本在 ApplicationVersion 中保持一致。</li>
 *   <li><b>澄清会话持久化</b>：存入 PostgreSQL 而非仅依赖 Redis。锁定
 *       versionId 和 intentTreeVersionId，防止澄清过程中新版本发布造成漂移。</li>
 *   <li><b>Agent 兜底</b>：未命中、未映射或语义决策 DEFER 统一交给 Application 的 fallback Agent。</li>
 *   <li><b>分数可解释</b>：intentScore = max(原型相似度, 各正例相似度)，
 *       保持先简单可解释，权重融合待评测数据验证后增加。</li>
 *   <li><b>每叶子一个最高分</b>：SQL 层面按 intent_code 分组取 MAX，
 *       避免同一叶子的多个正例向量重复出现在候选列表中。</li>
 * </ul>
 *
 * <h2>常量说明</h2>
 * <ul>
 *   <li>{@code MAX_CLARIFICATION_ROUNDS = 2}：最多两轮澄清，超限进入失败兜底</li>
 *   <li>{@code CLARIFICATION_TTL_MINUTES = 10}：澄清会话过期时间</li>
 *   <li>{@code CANCEL_WORDS}：用户主动取消澄清的关键词集合</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class IntentRoutingServiceImpl implements IntentRoutingService {

    /**
     * 澄清会话最大轮次上限
     */
    private static final int MAX_CLARIFICATION_ROUNDS = 2;
    /**
     * 澄清会话过期时间（分钟）
     */
    private static final int CLARIFICATION_TTL_MINUTES = 10;
    /**
     * 用户主动取消澄清的关键词
     */
    private static final Set<String> CANCEL_WORDS = Set.of("取消", "算了", "不用了", "退出");

    /**
     * 意图树版本只读查询
     */
    private final IntentTreeRuntimeRepo intentTreeRepo;
    /** 复用发布意图树的纯候选召回能力。 */
    private final IntentCandidateRetriever candidateRetriever;
    /** 使用锁定模型和Prompt在候选范围内完成强类型语义决策。 */
    private final IntentSemanticDecisionService semanticDecisionService;
    /**
     * 澄清会话持久化
     */
    private final IntentRoutingSessionRepo sessionRepo;
    /** 路由完成后用短事务追加决策事实，并关联可选澄清会话。 */
    private final IntentRoutingDecisionPersistenceServiceImpl decisionPersistenceService;

    /**
     * 意图路由入口。
     *
     * <p>每一条用户消息到达时调用此方法，优先级为：
     * <ol>
     *   <li>未启用意图路由 → 交回 Application fallback Agent</li>
     *   <li>存在 PENDING 澄清会话 → 恢复澄清流程</li>
     *   <li>否则 → 新请求全流程识别</li>
     * </ol>
     *
     * @param context               路由前已准备完成的共享对话上下文
     * @param clarificationResponse 可选的结构化澄清响应（前端按钮选择或自由文本补充）
     * @return 确定性路由结果，包含执行动作、目标、查询和可选的澄清事件
     */
    @Override
    public IntentRoutingResult route(ConversationContext context, ClarificationResponseRequest clarificationResponse) {

        AppRuntimeSnapshot application = context.getApplicationSnapshot();
        LoginUser loginUser = context.getLoginUser();
        ChatTurnBO turn = context.getTurn();
        String originalQuery = context.getOriginalQuery();
        ApplicationIntentBinding binding = application.definition().intentRouting();
        if (Objects.isNull(binding) || !binding.isEnabled()) {
            return persistDecision(context, null, List.of(), null,
                    IntentRoutingResult.skipped(context.routingQuery()));
        }
        // 加载发布版本中锁定的意图树版本快照
        RuntimeIntentTreeVersionEntity version = intentTreeRepo
                .findVersion(binding.getIntentTreeId(), binding.getIntentTreeVersionId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "Application锁定的意图树版本不存在"));
        IntentTreeDefinition definition = Objects.requireNonNull(version.getDefinitionSnapshot(), "意图树发布定义不能为空");
        if (definition.schemaVersion() != INTENT_TREE_SCHEMA_VERSION) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "意图树发布定义版本不受支持");
        }
        // 构建 code → node 索引，后续按编码快速查找节点详情
        Map<String, IntentNodeDefinition> nodesByCode = definition.nodes().stream()
                .collect(Collectors.toMap(IntentNodeDefinition::getCode, Function.identity()));
        RouteConfiguration configuration = RouteConfiguration.builder()
                .application(application)
                .binding(binding)
                .version(version)
                .definition(definition)
                .nodesByCode(nodesByCode)
                .build();

        // 步骤 1：检查是否存在待处理的澄清会话
        Optional<IntentRoutingSessionEntity> pending = findPending(application, turn, loginUser, clarificationResponse);
        if (pending.isPresent()) {
            // 步骤 2：恢复澄清流程，处理用户的选择或槽位输入
            IntentRoutingResult resumed = resumeClarification(pending.get(), originalQuery, clarificationResponse, configuration);
            return persistDecision(context, configuration, List.of(), null, resumed);
        }
        // 步骤 3：全新请求——向量化 → 召回 → 门控 → 执行/澄清
        return routeFresh(configuration, context, loginUser, turn);
    }

    /**
     * 查找待处理的澄清会话。
     *
     * <p>两种查找方式：</p>
     * <ol>
     *   <li>前端传递了 clarificationId → 精确查询指定会话并校验归属和版本</li>
     *   <li>无 clarificationId → 查找同一对话+用户+ApplicationVersion 下的最新 PENDING 会话</li>
     * </ol>
     *
     * <p>第二种方式支持纯文本回复（如"第一个"、"A10086"）自动关联到已有的澄清上下文。</p>
     *
     * @param application 运行时 Application 快照
     * @param turn        当前对话轮次
     * @param loginUser   当前用户
     * @param response    可选的澄清响应
     * @return 待处理的会话，无 PENDING 会话时为空
     */
    private Optional<IntentRoutingSessionEntity> findPending(AppRuntimeSnapshot application, ChatTurnBO turn, LoginUser loginUser, ClarificationResponseRequest response) {
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
            return Optional.of(session);
        }
        // 自动关联：查找同一对话中的最新 PENDING 会话
        return sessionRepo.findPending(turn.conversationId(), loginUser.id(), application.versionId());
    }

    /**
     * 恢复已有的澄清会话，处理用户本次的响应。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>检查过期：过期 → 状态标记 EXPIRED，返回显式延迟决策</li>
     *   <li>检查取消：用户回复取消词 → 状态标记 CANCELLED，返回显式延迟决策</li>
     *   <li>歧义消解类型 → 解析用户的选择（编码/名称/序号）→ 消歧成功则继续检查槽位</li>
     *   <li>缺槽位类型 → 采集用户提供的槽位值</li>
     * </ol>
     *
     * @param session     已有的 PENDING 澄清会话
     * @param query       用户本次回复文本
     * @param response    可选的澄清响应
     * @param configuration 已锁定的路由配置
     * @return 路由结果
     */
    private IntentRoutingResult resumeClarification(IntentRoutingSessionEntity session, String query,
                                                    ClarificationResponseRequest response,
                                                    RouteConfiguration configuration) {
        // 会话已过期 → 标记过期，本条消息重新识别
        if (session.getExpiresAt().isBefore(OffsetDateTime.now())) {
            session.setStatus(IntentRoutingSessionStatus.EXPIRED);
            sessionRepo.update(session);
            return IntentRoutingResult.deferUnmatched(query, IntentRoutingReason.CLARIFICATION_EXPIRED);
        }
        // 提取用户提供的值（优先从结构化响应中取，其次用原始文本）
        String suppliedValue = structuredValue(response, query);
        // 用户主动取消
        if (CANCEL_WORDS.contains(suppliedValue.trim())) {
            session.setStatus(IntentRoutingSessionStatus.CANCELLED);
            sessionRepo.update(session);
            return clarificationFailureResult(query, IntentRoutingReason.CLARIFICATION_CANCELLED);
        }
        if (session.getClarificationType() == IntentClarificationType.INTENT_DISAMBIGUATION) {
            // 歧义消解：尝试从用户回复中识别选择了哪个候选
            String intentCode = resolveCandidateCode(
                    session.getContext().getCandidateCodes(), suppliedValue, configuration.getNodesByCode());
            if (Objects.isNull(intentCode)) {
                // 无法识别 → 超轮次则失败，否则重复提问
                return repeatOrFail(session, configuration);
            }
            session.getContext().setIntentCode(intentCode);
            session.setStatus(IntentRoutingSessionStatus.RESOLVED);
            sessionRepo.update(session);
            // 消歧成功 → 继续检查该意图的槽位是否完整
            return resumeSelectedIntent(
                    session, originalQuery(session.getContext(), query), configuration,
                    configuration.getNodesByCode().get(intentCode),
                    reasonEvidence(IntentRoutingReason.CLARIFICATION_RESOLVED));
        }
        // MISSING_SLOT 类型：采集用户填入的槽位值
        return collectSlot(session, suppliedValue, query, configuration);
    }

    /**
     * 全新的候选路由流程：向量化 → 召回 → 安全门控 → 分发。
     *
     * <ol>
     *   <li>调用 EmbeddingGateway 将用户 query 向量化</li>
     *   <li>在指定意图树版本内按余弦相似度召回 Top-K 个候选</li>
     *   <li>过滤掉低于召回阈值的候选</li>
     *   <li>空候选 → 未命中兜底</li>
     *   <li>非空候选 → 锁定模型输出 SELECT / CLARIFY / DEFER 强类型决策</li>
     *   <li>SELECT / CLARIFY → 检查映射与槽位后执行或发起持久化澄清</li>
     *   <li>DEFER → 交给 fallback Agent</li>
     * </ol>
     *
     * @param configuration 已锁定的路由配置
     * @param context       当前对话上下文
     * @param loginUser     当前用户
     * @param turn          对话轮次
     * @return 路由结果
     */
    private IntentRoutingResult routeFresh(RouteConfiguration configuration, ConversationContext context,
                                           LoginUser loginUser, ChatTurnBO turn) {
        RuntimeIntentTreeVersionEntity version = configuration.getVersion();
        String query = context.routingQuery();

        IntentCandidateSet candidateSet = candidateRetriever.retrieve(new IntentCandidateRequest(version.getIntentTreeId(), version.getId(), query));
        if (candidateSet.candidates().isEmpty()) {
            return persistDecision(context, configuration, List.of(), null, fallbackResult(query));
        }
        IntentSemanticDecisionResult semanticResult = semanticDecisionService.decide(context, version, candidateSet.candidates());

        IntentSemanticDecision decision = semanticResult.getDecision();
        IntentRoutingResult routing = switch (decision.getAction()) {
            case SELECT -> routeSelected(configuration, query, loginUser, turn,
                    candidateSet.candidates(), decision, IntentRoutingReason.ROUTED_BY_SEMANTIC);
            case CLARIFY -> clarifyBySemanticDecision(configuration, query, loginUser, turn,
                    candidateSet.candidates(), decision);
            case DEFER -> defer(configuration, query, candidateSet.candidates(),
                    semanticDeferReason(decision.getReasonCode()));
        };
        return persistDecision(context, configuration, candidateSet.candidates(), semanticResult, routing);
    }

    private static IntentRoutingReason semanticDeferReason(IntentDecisionReason reason) {
        if (reason == IntentDecisionReason.SEMANTIC_DECISION_FAILED) {
            return IntentRoutingReason.SEMANTIC_DECISION_FAILED;
        }
        if (reason == IntentDecisionReason.MODEL_BUDGET_EXHAUSTED) {
            return IntentRoutingReason.MODEL_BUDGET_EXHAUSTED;
        }
        return IntentRoutingReason.SEMANTIC_DEFER;
    }

    /**
     * 追加本次路由的安全审计事实，并把模型用量附加到路由结果供 Application 并账。
     */
    private IntentRoutingResult persistDecision(ConversationContext context,
                                                RouteConfiguration configuration,
                                                List<IntentCandidate> candidates,
                                                IntentSemanticDecisionResult semanticResult,
                                                IntentRoutingResult routing) {
        TokenUsage usage = Objects.isNull(semanticResult)
                ? TokenUsage.empty() : Objects.requireNonNullElseGet(semanticResult.getTokenUsage(), TokenUsage::empty);
        IntentRoutingReason reason = routingReason(routing);
        RuntimeIntentTreeVersionEntity version = Objects.isNull(configuration) ? null : configuration.getVersion();
        IntentRoutingDecisionEntity decision = IntentRoutingDecisionEntity.builder()
                .conversationId(context.getTurn().conversationId())
                .userId(context.getLoginUser().id())
                .userMessageId(context.getTurn().userMessageId())
                .assistantMessageId(context.getTurn().assistantMessageId())
                .applicationId(context.getApplicationSnapshot().applicationId())
                .applicationVersionId(context.getApplicationSnapshot().versionId())
                .intentTreeId(Objects.isNull(version) ? null : version.getIntentTreeId())
                .intentTreeVersionId(Objects.isNull(version) ? null : version.getId())
                .embeddingModelInstanceId(Objects.isNull(version) ? null : version.getEmbeddingModelInstanceId())
                .chatModelInstanceId(Objects.isNull(semanticResult) ? null : semanticResult.getModelInstanceId())
                .promptVersionId(Objects.isNull(semanticResult) ? null : semanticResult.getPromptVersionId())
                .action(routing.getAction())
                .reason(reason)
                .selectedIntentCode(selectedIntentCode(routing, semanticResult))
                .capabilityCode(Objects.isNull(routing.getMapping()) ? null : routing.getMapping().getCapabilityCode())
                .candidateEvidence(decisionCandidates(configuration, candidates))
                .semanticDecision(semanticDecisionSnapshot(semanticResult))
                .inputTokens(usage.inputTokens())
                .outputTokens(usage.outputTokens())
                .totalTokens(usage.totalTokens())
                .modelCalls(Objects.isNull(semanticResult) ? 0 : semanticResult.getModelCalls())
                .retryCount(Objects.isNull(semanticResult) ? 0 : semanticResult.getRetryCount())
                .durationMillis(Objects.isNull(semanticResult) ? 0L : semanticResult.getDurationMillis())
                .build();

        Long decisionId = decisionPersistenceService.append(decision, clarificationId(routing));
        return routing.toBuilder()
                .decisionId(decisionId)
                .tokenUsage(usage)
                .modelCalls(decision.getModelCalls())
                .retryCount(decision.getRetryCount())
                .durationMillis(decision.getDurationMillis())
                .build();
    }

    private static IntentRoutingReason routingReason(IntentRoutingResult routing) {
        if (Objects.nonNull(routing.getRoutingEvidence())
                && Objects.nonNull(routing.getRoutingEvidence().getReason())) {
            return routing.getRoutingEvidence().getReason();
        }
        return switch (routing.getAction()) {
            case SKIPPED -> IntentRoutingReason.ROUTING_DISABLED;
            case CLARIFICATION -> IntentRoutingReason.SEMANTIC_CLARIFICATION;
            case DEFER_TO_AGENT -> IntentRoutingReason.NO_CANDIDATE;
            case FIXED_RESPONSE, EXECUTE_AGENT, EXECUTE_WORKFLOW -> IntentRoutingReason.ROUTED_BY_SEMANTIC;
        };
    }

    private static List<IntentRoutingDecisionCandidate> decisionCandidates(
            RouteConfiguration configuration, List<IntentCandidate> candidates) {
        return candidates.stream()
                .map(candidate -> new IntentRoutingDecisionCandidate(
                        candidate.intentCode(), candidate.getRank(), candidate.similarity(),
                        Objects.isNull(configuration) ? null : findMapping(configuration, candidate.intentCode())
                                .map(ApplicationIntentMapping::getCapabilityCode)
                                .orElse(null)))
                .toList();
    }

    private static IntentSemanticDecisionSnapshot semanticDecisionSnapshot(
            IntentSemanticDecisionResult semanticResult) {
        if (Objects.isNull(semanticResult) || Objects.isNull(semanticResult.getDecision())) {
            return null;
        }
        IntentSemanticDecision decision = semanticResult.getDecision();
        return IntentSemanticDecisionSnapshot.builder()
                .action(decision.getAction())
                .reason(decision.getReasonCode())
                .selectedIntentCode(decision.getSelectedIntentCode())
                .alternativeIntentCodes(decision.alternativeIntentCodes())
                .recognizedSlotCodes(decision.recognizedSlots().stream()
                        .map(IntentRecognizedSlot::getSlotCode)
                        .toList())
                .build();
    }

    private static String selectedIntentCode(IntentRoutingResult routing,
                                             IntentSemanticDecisionResult semanticResult) {
        if (CharSequenceUtil.isNotBlank(routing.getIntentCode())) {
            return routing.getIntentCode();
        }
        if (Objects.isNull(semanticResult) || Objects.isNull(semanticResult.getDecision())) {
            return null;
        }
        return semanticResult.getDecision().getSelectedIntentCode();
    }

    private static Long clarificationId(IntentRoutingResult routing) {
        if (routing.getAction() != IntentRoutingAction.CLARIFICATION
                || Objects.isNull(routing.getClarification())) {
            return null;
        }
        try {
            return Long.valueOf(routing.getClarification().getClarificationId());
        } catch (NumberFormatException exception) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "澄清会话ID格式不正确");
        }
    }

    private IntentRoutingResult routeSelected(RouteConfiguration configuration, String query,
                                              LoginUser loginUser, ChatTurnBO turn,
                                              List<IntentCandidate> candidates,
                                              IntentSemanticDecision decision,
                                              IntentRoutingReason evidenceReason) {
        IntentCandidate selected = findCandidate(candidates, decision.getSelectedIntentCode());
        if (!hasMapping(configuration, selected.intentCode())) {
            return defer(configuration, query, List.of(selected), IntentRoutingReason.UNMAPPED_CANDIDATE);
        }
        IntentNodeDefinition node = configuration.getNodesByCode().get(selected.intentCode());
        if (Objects.isNull(node)) {
            return defer(configuration, query, List.of(selected), IntentRoutingReason.UNMAPPED_CANDIDATE);
        }
        Map<String, String> collectedSlots = recognizedSlotValues(decision.recognizedSlots());
        List<String> missingSlots = missingSlots(node, collectedSlots);
        IntentRoutingEvidence evidence = evidence(evidenceReason, List.of(selected));
        if (missingSlots.isEmpty()) {
            return target(configuration, node.getCode(), enrichedQuery(query, collectedSlots), evidence);
        }
        // 只有确实需要用户补充时才创建持久化会话，完整意图直接进入执行目标。
        IntentClarificationContext clarificationContext = IntentClarificationContext.builder()
                .originalQuery(query)
                .intentCode(node.getCode())
                .collectedSlots(collectedSlots)
                .missingSlotCodes(missingSlots)
                .build();
        IntentRoutingSessionEntity session = createSession(turn, loginUser, configuration,
                IntentClarificationType.MISSING_SLOT, clarificationContext);
        return slotClarification(session, node, evidence);
    }

    private IntentRoutingResult clarifyBySemanticDecision(RouteConfiguration configuration, String query,
                                                          LoginUser loginUser, ChatTurnBO turn,
                                                          List<IntentCandidate> candidates,
                                                          IntentSemanticDecision decision) {
        if (CharSequenceUtil.isNotBlank(decision.getSelectedIntentCode())) {
            return routeSelected(configuration, query, loginUser, turn, candidates, decision,
                    IntentRoutingReason.SEMANTIC_CLARIFICATION);
        }
        List<IntentCandidate> alternatives = candidates.stream()
                .filter(candidate -> decision.alternativeIntentCodes().contains(candidate.intentCode()))
                .toList();
        List<ApplicationIntentMapping> mappings = alternatives.stream()
                .map(candidate -> findMapping(configuration, candidate.intentCode()).orElse(null))
                .toList();
        if (mappings.stream().anyMatch(Objects::isNull)) {
            return defer(configuration, query, alternatives, IntentRoutingReason.UNMAPPED_CANDIDATE);
        }
        List<String> candidateCodes = alternatives.stream().map(IntentCandidate::intentCode).toList();
        IntentClarificationContext clarificationContext = IntentClarificationContext.builder()
                .originalQuery(query)
                .candidateCodes(candidateCodes)
                .collectedSlots(new LinkedHashMap<>())
                .build();
        IntentRoutingSessionEntity session = createSession(turn, loginUser, configuration,
                IntentClarificationType.INTENT_DISAMBIGUATION, clarificationContext);
        return clarification(session, ambiguityQuestion(candidateCodes, configuration.getNodesByCode()),
                candidateCodes, configuration.getNodesByCode(),
                evidence(IntentRoutingReason.SEMANTIC_CLARIFICATION, alternatives));
    }

    /** 消歧后的已有会话继续收集必填槽位；无需补充时直接返回执行目标。 */
    private IntentRoutingResult resumeSelectedIntent(IntentRoutingSessionEntity session, String query,
                                                     RouteConfiguration configuration, IntentNodeDefinition node,
                                                     IntentRoutingEvidence evidence) {
        if (!hasMapping(configuration, node.getCode())) {
            return IntentRoutingResult.deferUnmatched(query, IntentRoutingReason.UNMAPPED_CANDIDATE);
        }
        Map<String, String> collectedSlots = Objects.requireNonNullElseGet(
                session.getContext().getCollectedSlots(), LinkedHashMap::new);
        List<String> missingSlots = missingSlots(node, collectedSlots);
        if (missingSlots.isEmpty()) {
            return target(configuration, node.getCode(), enrichedQuery(query, collectedSlots), evidence);
        }
        // 复用消歧会话的身份，补槽位阶段重新开始计轮和有效期。
        IntentClarificationContext context = session.getContext();
        context.setOriginalQuery(query);
        context.setIntentCode(node.getCode());
        context.setMissingSlotCodes(missingSlots);
        context.setCollectedSlots(collectedSlots);
        session.setClarificationType(IntentClarificationType.MISSING_SLOT);
        session.setStatus(IntentRoutingSessionStatus.PENDING);
        session.setRoundNo(1);
        session.setExpiresAt(OffsetDateTime.now().plusMinutes(CLARIFICATION_TTL_MINUTES));
        sessionRepo.update(session);
        return slotClarification(session, node, evidence);
    }

    private static List<String> missingSlots(IntentNodeDefinition node, Map<String, String> collectedSlots) {
        return requiredSlots(node).stream()
                .filter(slotCode -> !collectedSlots.containsKey(slotCode))
                .toList();
    }

    private static IntentCandidate findCandidate(List<IntentCandidate> candidates, String intentCode) {
        return candidates.stream()
                .filter(candidate -> Objects.equals(candidate.intentCode(), intentCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "语义决策选择了候选外意图"));
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

    /**
     * 从用户回复中采集一个槽位值。
     *
     * <p>按 FIFO 顺序取出 {@code missingSlotCodes} 的第一个槽位编码，
     * 将用户输入作为该槽位的值存入 {@code collectedSlots}。</p>
     *
     * <p>如果采集后还有剩余槽位 → 检查轮次上限：未超限则继续提问下一槽位；
     * 超限则标记 FAILED 并返回兜底文案。所有槽位采集完成 → 标记 COMPLETED，
     * 拼接 enrichedQuery 返回执行目标。</p>
     *
     * @param session       澄清会话
     * @param value         用户提供的槽位值
     * @param originalQuery 外层传入的原始查询（用于兜底）
     * @param configuration 已锁定的路由配置
     * @return 继续澄清 或 执行目标结果
     */
    private IntentRoutingResult collectSlot(IntentRoutingSessionEntity session, String value, String originalQuery,
                                            RouteConfiguration configuration) {
        IntentClarificationContext context = session.getContext();
        List<String> missing = new ArrayList<>(context.getMissingSlotCodes());
        // 防御：状态异常（应该不会发生，但做安全处理）
        if (missing.isEmpty()) {
            session.setStatus(IntentRoutingSessionStatus.FAILED);
            sessionRepo.update(session);
            return clarificationFailureResult(originalQuery, IntentRoutingReason.CLARIFICATION_FAILED);
        }
        // FIFO：取出第一个待收集的槽位
        String slotCode = missing.removeFirst();
        IntentSlotDefinition slotDefinition = findSlotDefinition(configuration, context.getIntentCode(), slotCode);
        String normalizedValue = value.trim();
        if (Objects.nonNull(slotDefinition) && !isValidSlotValue(slotDefinition, normalizedValue)) {
            // 格式校验失败：不写入槽位，轮次内重问并提示格式要求，避免脏数据进入执行链路
            return repeatSlotQuestion(session, slotDefinition, configuration);
        }
        Map<String, String> collected = new LinkedHashMap<>(Objects.requireNonNullElse(context.getCollectedSlots(), Map.of()));
        collected.put(slotCode, normalizedValue);
        context.setCollectedSlots(collected);
        context.setMissingSlotCodes(missing);
        // 还有剩余槽位 → 检查轮次
        if (!missing.isEmpty()) {
            if (session.getRoundNo() >= MAX_CLARIFICATION_ROUNDS) {
                // 超轮次 → 失败兜底
                session.setStatus(IntentRoutingSessionStatus.FAILED);
                sessionRepo.update(session);
                return clarificationFailureResult(originalQuery, IntentRoutingReason.CLARIFICATION_FAILED);
            }
            // 轮次 +1，更新过期时间，继续提问下一个槽位
            session.setRoundNo(session.getRoundNo() + 1);
            session.setExpiresAt(OffsetDateTime.now().plusMinutes(CLARIFICATION_TTL_MINUTES));
            sessionRepo.update(session);
            return slotClarification(session, configuration.getNodesByCode().get(context.getIntentCode()),
                    reasonEvidence(IntentRoutingReason.CLARIFICATION_RETRY));
        }
        // 所有槽位采集完成 → 拼接 enrichedQuery，返回执行目标
        session.setStatus(IntentRoutingSessionStatus.COMPLETED);
        sessionRepo.update(session);
        return target(configuration, context.getIntentCode(),
                enrichedQuery(originalQuery(context, originalQuery), collected),
                reasonEvidence(IntentRoutingReason.CLARIFICATION_RESOLVED));
    }

    /**
     * 槽位值格式校验失败：轮次内重问并提示格式要求，超限标记失败兜底。
     *
     * @param session        澄清会话
     * @param slotDefinition 校验失败的槽位定义（用于格式提示）
     * @param configuration  已锁定的路由配置
     * @return 继续澄清 或 失败兜底结果
     */
    private IntentRoutingResult repeatSlotQuestion(IntentRoutingSessionEntity session, IntentSlotDefinition slotDefinition,
                                                   RouteConfiguration configuration) {
        if (session.getRoundNo() >= MAX_CLARIFICATION_ROUNDS) {
            session.setStatus(IntentRoutingSessionStatus.FAILED);
            sessionRepo.update(session);
            return clarificationFailureResult(session.getContext().getOriginalQuery(),
                    IntentRoutingReason.CLARIFICATION_FAILED);
        }
        session.setRoundNo(session.getRoundNo() + 1);
        session.setExpiresAt(OffsetDateTime.now().plusMinutes(CLARIFICATION_TTL_MINUTES));
        sessionRepo.update(session);
        String question = "「" + slotDefinition.getName() + "」格式不正确，请按"
                + slotDefinition.getValueType().getDescribe() + "格式重新输入。";
        ChatClarificationEventBO event = ChatClarificationEventBO.builder()
                .clarificationId(String.valueOf(session.getId()))
                .question(question)
                .options(List.of())
                .round(session.getRoundNo())
                .expiresAt(session.getExpiresAt())
                .build();
        return IntentRoutingResult.builder()
                .action(IntentRoutingAction.CLARIFICATION)
                .response(question)
                .clarification(event)
                .routingEvidence(reasonEvidence(IntentRoutingReason.CLARIFICATION_RETRY))
                .build();
    }

    /**
     * 在叶子节点定义中查找指定编码的槽位定义；节点或槽位不存在时返回空。
     */
    private static IntentSlotDefinition findSlotDefinition(RouteConfiguration configuration, String intentCode, String slotCode) {
        IntentNodeDefinition node = configuration.getNodesByCode().get(intentCode);
        if (Objects.isNull(node) || Objects.isNull(node.getSlots())) {
            return null;
        }
        return node.getSlots().stream()
                .filter(slot -> Objects.equals(slot.getCode(), slotCode))
                .findFirst()
                .orElse(null);
    }

    /**
     * 按槽位声明的值类型校验用户输入；未声明类型时视为自由文本不校验。
     */
    private static boolean isValidSlotValue(IntentSlotDefinition slot, String value) {
        IntentSlotValueType type = slot.getValueType();
        if (Objects.isNull(type)) {
            return true;
        }
        return switch (type) {
            case STRING -> true;
            case NUMBER -> isNumber(value);
            case DATE -> isDate(value);
            case BOOLEAN -> isBoolean(value);
        };
    }

    /** 数字：可解析为 BigDecimal 视为合法。 */
    private static boolean isNumber(String value) {
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    /** 日期：支持 2026-01-01、2026/1/5 等常见分隔格式，且必须是真实存在的日期。 */
    private static final DateTimeFormatter SLOT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-M-d");

    private static boolean isDate(String value) {
        try {
            // 将 / 分隔符归一为 -，yyyy-M-d 允许月日不补零
            LocalDate.parse(value.trim().replace('/', '-'), SLOT_DATE_FORMAT);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    /** 布尔：接受英文、中文及 1/0 表达。 */
    private static boolean isBoolean(String value) {
        return Set.of("true", "false", "是", "否", "1", "0", "开", "关")
                .contains(value.toLowerCase(Locale.ROOT));
    }

    /**
     * 澄清重试或失败判断。
     *
     * <p>当用户对歧义澄清的回复无法匹配任何候选时调用：
     * 轮次未超限 → 轮次 +1，重复提问；轮次超限 → 标记 FAILED，返回兜底文案。</p>
     *
     * @param session     澄清会话
     * @param configuration 已锁定的路由配置
     * @return 重复澄清 或 失败兜底
     */
    private IntentRoutingResult repeatOrFail(IntentRoutingSessionEntity session,
                                             RouteConfiguration configuration) {
        if (session.getRoundNo() >= MAX_CLARIFICATION_ROUNDS) {
            session.setStatus(IntentRoutingSessionStatus.FAILED);
            sessionRepo.update(session);
            return clarificationFailureResult(session.getContext().getOriginalQuery(),
                    IntentRoutingReason.CLARIFICATION_FAILED);
        }
        session.setRoundNo(session.getRoundNo() + 1);
        session.setExpiresAt(OffsetDateTime.now().plusMinutes(CLARIFICATION_TTL_MINUTES));
        sessionRepo.update(session);
        List<String> codes = session.getContext().getCandidateCodes();
        return clarification(session, ambiguityQuestion(codes, configuration.getNodesByCode()),
                codes, configuration.getNodesByCode(),
                reasonEvidence(IntentRoutingReason.CLARIFICATION_RETRY));
    }

    /**
     * 创建新的澄清会话并持久化。
     *
     * <p>会话创建时锁定 versionId 和 intentTreeVersionId，
     * 设置 PENDING 状态、过期时间（10 分钟）和初始轮次（1）。</p>
     *
     * @param turn                对话轮次信息
     * @param loginUser           当前用户
     * @param configuration       已锁定的路由配置
     * @param type                澄清类型
     * @param context             初始上下文
     * @return 已持久化的会话实体（含数据库生成的主键）
     */
    private IntentRoutingSessionEntity createSession(ChatTurnBO turn, LoginUser loginUser,
                                                     RouteConfiguration configuration,
                                                     IntentClarificationType type,
                                                     IntentClarificationContext context) {
        IntentRoutingSessionEntity session = new IntentRoutingSessionEntity();
        session.setConversationId(turn.conversationId());
        session.setUserId(loginUser.id());
        session.setApplicationVersionId(configuration.getApplication().versionId());
        session.setIntentTreeVersionId(configuration.getVersion().getId());
        session.setStatus(IntentRoutingSessionStatus.PENDING);
        session.setClarificationType(type);
        session.setRoundNo(1);
        session.setContext(context);
        session.setExpiresAt(OffsetDateTime.now().plusMinutes(CLARIFICATION_TTL_MINUTES));
        sessionRepo.insert(session);
        return session;
    }

    /**
     * 查找意图映射并构建执行目标结果。
     *
     * <p>根据意图编码在 ApplicationIntentBinding.mappings 中查找映射记录，
     * 按 targetType 转换为对应的 IntentRoutingAction：
     * WORKFLOW → EXECUTE_WORKFLOW、AGENT → EXECUTE_AGENT、
     * FIXED_RESPONSE → FIXED_RESPONSE。</p>
     *
     * <p>意图树候选允许未绑定执行能力；未配置映射时交给 fallback Agent。</p>
     *
     * @param configuration 已锁定的路由配置
     * @param intentCode 叶子意图编码
     * @param query      执行时使用的查询（原始请求或拼接了槽位的增强版）
     * @param evidence   路由决策证据（fresh 识别路径传入用于观测；恢复路径传 null）
     * @return 包含执行动作、映射和目标查询的结果
     */
    private static IntentRoutingResult target(RouteConfiguration configuration, String intentCode, String query,
                                              IntentRoutingEvidence evidence) {
        Optional<ApplicationIntentMapping> resolvedMapping = findMapping(configuration, intentCode);
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

    private static IntentRoutingResult fallbackResult(String query) {
        return IntentRoutingResult.deferUnmatched(query, IntentRoutingReason.NO_CANDIDATE);
    }

    private static IntentRoutingResult clarificationFailureResult(String query, IntentRoutingReason reason) {
        return IntentRoutingResult.deferUnmatched(query, reason);
    }

    private static IntentRoutingEvidence reasonEvidence(IntentRoutingReason reason) {
        return IntentRoutingEvidence.builder()
                .reason(reason)
                .candidates(List.of())
                .build();
    }

    private static boolean hasMapping(RouteConfiguration configuration, String intentCode) {
        return findMapping(configuration, intentCode).isPresent();
    }

    private static Optional<ApplicationIntentMapping> findMapping(
            RouteConfiguration configuration, String intentCode) {
        return configuration.getBinding().mappings().stream()
                .filter(mapping -> Objects.equals(mapping.getIntentCode(), intentCode))
                .findFirst();
    }

    private static IntentRoutingResult defer(
            RouteConfiguration configuration,
            String query,
            List<IntentCandidate> candidates,
            IntentRoutingReason reason) {
        Map<String, CapabilityDefinition> capabilitiesByCode = configuration.getApplication()
                .definition().capabilityCatalog().capabilities().stream()
                .collect(Collectors.toMap(CapabilityDefinition::getCode, Function.identity()));
        List<IntentRoutingEvidenceCandidate> evidenceCandidates = candidates.stream()
                .map(candidate -> toEvidenceCandidate(configuration, capabilitiesByCode, candidate))
                .toList();
        return IntentRoutingResult.defer(query, IntentRoutingEvidence.builder()
                .reason(reason)
                .candidates(evidenceCandidates)
                .build());
    }

    private static IntentRoutingEvidenceCandidate toEvidenceCandidate(
            RouteConfiguration configuration,
            Map<String, CapabilityDefinition> capabilitiesByCode,
            IntentCandidate candidate) {
        ApplicationIntentMapping mapping = findMapping(configuration, candidate.intentCode()).orElse(null);
        String capabilityCode = Objects.isNull(mapping) ? null : mapping.getCapabilityCode();
        CapabilityDefinition capability = capabilitiesByCode.get(capabilityCode);
        IntentNodeDefinition node = configuration.getNodesByCode().get(candidate.intentCode());
        return IntentRoutingEvidenceCandidate.builder()
                .intentCode(candidate.intentCode())
                .intentName(Objects.isNull(node) ? candidate.intentCode() : node.getName())
                .similarity(candidate.similarity())
                .capabilityCode(capabilityCode)
                .capabilityDescription(Objects.isNull(capability) ? null : capability.getDescription())
                .build();
    }

    /**
     * 构建澄清事件结果。
     *
     * <p>将候选意图编码列表转换为结构化的 {@link ChatClarificationEventBO}，
     * 包含问题文本、选项列表（code + label）、轮次和过期时间。
     * 前端据此渲染澄清选项按钮或追问文本。</p>
     *
     * @param session     澄清会话（用于获取 sessionId、roundNo、expiresAt）
     * @param question    向用户展示的澄清问题文本
     * @param optionCodes 候选意图编码列表（歧义场景）；空列表（槽位场景）
     * @param nodesByCode code → 节点映射（用于获取节点名称作为选项 label）
     * @param evidence    路由决策证据（fresh 识别路径传入用于观测；恢复路径传 null）
     * @return CLARIFICATION 动作的路由结果
     */
    private static IntentRoutingResult clarification(IntentRoutingSessionEntity session, String question, List<String> optionCodes, Map<String, IntentNodeDefinition> nodesByCode,
                                                     IntentRoutingEvidence evidence) {
        List<ChatClarificationEventBO.ClarificationOptionBO> options = optionCodes.stream()
                .map(code -> new ChatClarificationEventBO.ClarificationOptionBO(code, Optional.ofNullable(nodesByCode.get(code))
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
                .routingEvidence(evidence)
                .build();
    }

    /**
     * 将识别候选转换为路由决策证据，候选仅保留编码与相似度。
     */
    private static IntentRoutingEvidence evidence(IntentRoutingReason reason,
                                                  List<IntentCandidate> candidates) {
        List<IntentRoutingEvidenceCandidate> evidenceCandidates = candidates.stream()
                .map(candidate -> IntentRoutingEvidenceCandidate.builder()
                        .intentCode(candidate.intentCode())
                        .similarity(candidate.similarity())
                        .build())
                .toList();
        return IntentRoutingEvidence.builder()
                .reason(reason)
                .candidates(evidenceCandidates)
                .build();
    }

    /**
     * 构建缺槽位澄清事件。
     *
     * <p>取出 missingSlotCodes 队列的第一个槽位编码，查找节点定义中对应的槽位名称，
     * 生成澄清问题。优先使用节点配置的 clarificationQuestion 模板，
     * 未配置时用默认格式："请补充「槽位名称」。"。</p>
     *
     * @param session 澄清会话
     * @param node    叶子意图节点（用于查找槽位定义和澄清模板）
     * @return 槽位澄清事件
     */
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
        return clarification(session, question, List.of(), Map.of(), evidence);
    }

    /**
     * 从用户回复中解析意图候选选择。
     *
     * <p>支持三种匹配方式（按优先级）：</p>
     * <ol>
     *   <li>精确匹配候选编码（大小写不敏感）</li>
     *   <li>精确匹配节点名称</li>
     *   <li>序数匹配："1"/"一"/"第一个"/"第一项" → 候选列表第 0 项；
     *       "2"/"二"/"第二个"/"第二项" → 候选列表第 1 项，以此类推</li>
     * </ol>
     *
     * <p>返回 null 表示无法识别用户的意图选择。</p>
     *
     * @param candidateCodes 候选意图编码列表（按相似度降序）
     * @param value          用户回复的文本（已 trim 和结构化提取）
     * @param nodesByCode    code → 节点映射
     * @return 匹配到的意图编码，无法识别时返回 null
     */
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

    /**
     * 判断用户输入是否为候选列表中第 index 项的序数表达。
     *
     * <p>index 0 → "1"、"一"、"第一个"、"第一项"<br>
     * index 1 → "2"、"二"、"第二个"、"第二项"<br>
     * index 2 → "3"、"三"、"第三个"、"第三项"<br>
     * 超过 3 的索引 → 仅匹配数字字符串（如 "4"、"5"）</p>
     *
     * @param value 用户输入的文本
     * @param index 候选列表中的位置索引
     * @return 是否匹配
     */
    private static boolean ordinalMatches(String value, int index) {
        return switch (index) {
            case 0 -> Set.of("1", "一", "第一个", "第一项").contains(value);
            case 1 -> Set.of("2", "二", "第二个", "第二项").contains(value);
            case 2 -> Set.of("3", "三", "第三个", "第三项").contains(value);
            default -> value.equals(String.valueOf(index + 1));
        };
    }

    /**
     * 获取叶子节点的必填槽位编码列表。
     *
     * @param node 意图节点
     * @return 所有 required=true 的槽位编码列表，按定义顺序排列
     */
    private static List<String> requiredSlots(IntentNodeDefinition node) {
        return Optional.ofNullable(node.getSlots()).orElse(List.of()).stream()
                .filter(IntentSlotDefinition::isRequired)
                .map(IntentSlotDefinition::getCode)
                .toList();
    }

    /**
     * 生成歧义澄清的选择问题文本。
     *
     * <p>格式："我还不能确定你的具体意图，请选择：1. 名称A；2. 名称B"</p>
     *
     * @param codes       候选意图编码列表
     * @param nodesByCode code → 节点映射
     * @return 带编号选项的澄清问题文本
     */
    private static String ambiguityQuestion(List<String> codes, Map<String, IntentNodeDefinition> nodesByCode) {
        String options = java.util.stream.IntStream.range(0, codes.size())
                .mapToObj(index -> (index + 1) + ". " + nodesByCode.get(codes.get(index)).getName())
                .collect(Collectors.joining("；"));
        return "我还不能确定你的具体意图，请选择：" + options;
    }

    /**
     * 从澄清响应中提取用户提供的值。
     *
     * <p>优先级：结构化 optionCode > 结构化 value > 原始文本 query</p>
     *
     * @param response 可选的澄清响应（可能为 null）
     * @param query    用户的自由文本输入
     * @return 提取后的有效值
     */
    private static String structuredValue(ClarificationResponseRequest response, String query) {
        if (Objects.isNull(response)) {
            return query;
        }
        if (CharSequenceUtil.isNotBlank(response.getOptionCode())) {
            return response.getOptionCode();
        }
        return CharSequenceUtil.isNotBlank(response.getValue()) ? response.getValue() : query;
    }

    /**
     * 将已收集的槽位拼接到执行查询尾部。
     *
     * <p>格式：原始请求 + "\n已确认参数：orderNo=A10086，date=2026-01-01"</p>
     *
     * <p>确认：V1 采用纯文本拼接，后续可改为结构化注入（如 JSON 格式）。</p>
     *
     * @param query          原始请求文本
     * @param collectedSlots 已收集的槽位映射
     * @return 增强后的查询文本
     */
    private static String enrichedQuery(String query, Map<String, String> collectedSlots) {
        if (collectedSlots.isEmpty()) {
            return query;
        }
        String slots = collectedSlots.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("，"));
        return query + "\n已确认参数：" + slots;
    }

    /**
     * 获取澄清开始时的原始用户请求文本。
     *
     * <p>澄清完成后使用原始请求 + 已收集槽位构建执行查询，而非用户在澄清过程中的简短回复。</p>
     *
     * @param context       澄清上下文（可能包含 originalQuery）
     * @param fallbackQuery 兜底：上下文为空时使用当前 query
     * @return 原始用户请求文本
     */
    private static String originalQuery(IntentClarificationContext context, String fallbackQuery) {
        return CharSequenceUtil.isNotBlank(context.getOriginalQuery())
                ? context.getOriginalQuery()
                : fallbackQuery;
    }

    /** 单次路由使用的已发布快照，避免离散参数错位。 */
    @Getter
    @Builder
    private static class RouteConfiguration {
        private final AppRuntimeSnapshot application;
        private final ApplicationIntentBinding binding;
        private final RuntimeIntentTreeVersionEntity version;
        private final IntentTreeDefinition definition;
        private final Map<String, IntentNodeDefinition> nodesByCode;
    }
}

package com.lingxi.workbench.application.runtime.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.application.runtime.common.enums.IntentClarificationType;
import com.lingxi.workbench.application.runtime.common.enums.IntentRecognitionStatus;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingSessionStatus;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.IntentClarificationContext;
import com.lingxi.workbench.application.runtime.model.IntentRecognitionCandidate;
import com.lingxi.workbench.application.runtime.model.IntentRecognitionRequest;
import com.lingxi.workbench.application.runtime.model.IntentRecognitionResult;
import com.lingxi.workbench.application.runtime.model.IntentRoutingResult;
import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingSessionEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.application.runtime.repo.IntentRoutingSessionRepo;
import com.lingxi.workbench.application.runtime.repo.IntentTreeRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.IntentRecognitionService;
import com.lingxi.workbench.application.runtime.service.IntentRoutingService;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.request.ClarificationResponseRequest;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentNodeDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentSlotDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

/**
 * 基于发布向量、确定性分差门控和持久化澄清会话的意图路由实现。
 *
 * <h2>路由入口</h2>
 * <p>{@link #route(ConversationContext, ClarificationResponseRequest)}
 * 是唯一公开入口，由 {@code ApplicationChatExecutionServiceImpl} 在
 * {@code ChatStreamServiceImpl.startChat()} 中调用。</p>
 *
 * <h2>执行流程</h2>
 * <ol>
 *   <li><b>前置检查</b>：Application 是否启用快速路由？未启用则交回执行大脑。</li>
 *   <li><b>加载版本快照</b>：读取 ApplicationVersion 中锁定的 IntentTreeVersion，
 *       解析 IntentTreeDefinition 并构建 code→node 索引。</li>
 *   <li><b>检查待处理澄清</b>：同一对话中是否有 PENDING 状态的澄清会话？
 *       有 → 恢复澄清流程，处理用户的选择或槽位输入。
 *       如会话已过期或取消 → 返回 null，重新识别。</li>
 *   <li><b>新请求识别</b>：向量化用户输入 → pgvector 余弦召回 Top-K 候选
 *       → 过滤低于阈值的候选 → 判断歧义 / 确定意图。</li>
 *   <li><b>结果分发</b>：
 *       <ul>
 *         <li>无候选 → 返回未命中兜底文案</li>
 *         <li>歧义 → 创建澄清会话，返回候选选项列表</li>
 *         <li>明确意图 → 检查槽位：缺必填槽位 → 澄清采集；槽位完整 → 返回执行目标</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h2>关键设计决策</h2>
 * <ul>
 *   <li><b>快速路由不使用 LLM 分类</b>：只在向量召回形成确定结果时直达锁定能力。</li>
 *   <li><b>澄清会话持久化</b>：存入 PostgreSQL 而非仅依赖 Redis。锁定
 *       versionId 和 intentTreeVersionId，防止澄清过程中新版本发布造成漂移。</li>
 *   <li><b>模型兜底</b>：Schema V5 未命中、未映射或不安全歧义均交回 Planner/默认ReAct。</li>
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
    /** 复用发布意图树的纯向量识别能力。 */
    private final IntentRecognitionService intentRecognitionService;
    /**
     * 澄清会话持久化
     */
    private final IntentRoutingSessionRepo sessionRepo;

    /**
     * 意图路由入口。
     *
     * <p>每一条用户消息到达时调用此方法，优先级为：
     * <ol>
     *   <li>未启用快速路由 → 交回 Planner 或默认 ReAct Agent</li>
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
        ApplicationIntentBinding binding = application.definition().optionalFastRoutes();
        if (Objects.isNull(binding) || !binding.isEnabled()) {
            return IntentRoutingResult.noMatch(context.routingQuery());
        }
        // 加载发布版本中锁定的意图树版本快照
        RuntimeIntentTreeVersionEntity version = intentTreeRepo
                .findVersion(binding.getIntentTreeId(), binding.getIntentTreeVersionId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "Application锁定的意图树版本不存在"));
        IntentTreeDefinition definition = Objects.requireNonNull(version.getDefinitionSnapshot(), "意图树发布定义不能为空");
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
            // 返回 null 表示会话已过期或取消 → 作为全新请求重新识别
            IntentRoutingResult resumed = resumeClarification(pending.get(), originalQuery, clarificationResponse, configuration);
            if (Objects.nonNull(resumed)) {
                return resumed;
            }
        }
        // 步骤 3：全新请求——向量化 → 召回 → 门控 → 执行/澄清
        return routeFresh(configuration, context.routingQuery(), loginUser, turn);
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
     * <p>返回值语义：</p>
     * <ul>
     *   <li>返回非 null → 澄清流程继续或完成，外层直接返回该结果</li>
     *   <li>返回 null → 会话已过期/取消，外层应作为全新请求重新识别</li>
     * </ul>
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>检查过期：过期 → 状态标记 EXPIRED，返回 null</li>
     *   <li>检查取消：用户回复取消词 → 状态标记 CANCELLED，返回兜底文案</li>
     *   <li>歧义消解类型 → 解析用户的选择（编码/名称/序号）→ 消歧成功则继续检查槽位</li>
     *   <li>缺槽位类型 → 采集用户提供的槽位值</li>
     * </ol>
     *
     * @param session     已有的 PENDING 澄清会话
     * @param query       用户本次回复文本
     * @param response    可选的澄清响应
     * @param binding     已发布的意图绑定配置
     * @param nodesByCode code → 节点定义映射
     * @return 路由结果，会话取消/过期时返回 null
     */
    private IntentRoutingResult resumeClarification(IntentRoutingSessionEntity session, String query,
                                                    ClarificationResponseRequest response,
                                                    RouteConfiguration configuration) {
        // 会话已过期 → 标记过期，本条消息重新识别
        if (session.getExpiresAt().isBefore(OffsetDateTime.now())) {
            session.setStatus(IntentRoutingSessionStatus.EXPIRED);
            sessionRepo.update(session);
            return IntentRoutingResult.noMatch(query);
        }
        // 提取用户提供的值（优先从结构化响应中取，其次用原始文本）
        String suppliedValue = structuredValue(response, query);
        // 用户主动取消
        if (CANCEL_WORDS.contains(suppliedValue.trim())) {
            session.setStatus(IntentRoutingSessionStatus.CANCELLED);
            sessionRepo.update(session);
            return clarificationFailureResult(query);
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
            return resolveSlotsOrTarget(
                    session, originalQuery(session.getContext(), query), configuration,
                    configuration.getNodesByCode().get(intentCode));
        }
        // MISSING_SLOT 类型：采集用户填入的槽位值
        return collectSlot(session, suppliedValue, query, configuration);
    }

    /**
     * 全新的意图识别流程：向量化 → 召回 → 门控 → 分发。
     *
     * <p>该链路不依赖 LLM，只在向量相似度和分差满足确定性门控时直达。</p>
     *
     * <ol>
     *   <li>调用 EmbeddingGateway 将用户 query 向量化</li>
     *   <li>在指定意图树版本内按余弦相似度召回 Top-K 个候选</li>
     *   <li>过滤掉低于召回阈值的候选</li>
     *   <li>空候选 → 未命中兜底</li>
     *   <li>Top-1 与 Top-2 分差小于歧义阈值 → 歧义澄清</li>
     *   <li>否则 → 确定意图，检查槽位 → 执行或缺槽位澄清</li>
     * </ol>
     *
     * @param configuration 已锁定的路由配置
     * @param query         用户输入文本
     * @param loginUser     当前用户
     * @param turn          对话轮次
     * @return 路由结果
     */
    private IntentRoutingResult routeFresh(RouteConfiguration configuration, String query,
                                           LoginUser loginUser, ChatTurnBO turn) {
        RuntimeIntentTreeVersionEntity version = configuration.getVersion();
        IntentRecognitionResult recognition = intentRecognitionService.recognize(new IntentRecognitionRequest(
                version.getIntentTreeId(), version.getId(), query));
        if (recognition.status() == IntentRecognitionStatus.NOT_MATCHED) {
            return fallbackResult(query);
        }
        IntentRecognitionCandidate topCandidate = recognition.candidates().getFirst();
        if (!hasMapping(configuration, topCandidate.intentCode())) {
            return IntentRoutingResult.noMatch(query);
        }
        // 可选模式只对全部候选都可确定执行的歧义提供选项，否则交由模型理解组合需求。
        if (recognition.status() == IntentRecognitionStatus.AMBIGUOUS) {
            if (recognition.candidates().stream().anyMatch(
                    candidate -> !hasMapping(configuration, candidate.intentCode()))) {
                return IntentRoutingResult.noMatch(query);
            }
            List<String> candidateCodes = recognition.candidates().stream()
                    .map(IntentRecognitionCandidate::intentCode)
                    .toList();
            IntentClarificationContext clarificationContext = IntentClarificationContext.builder()
                    .originalQuery(query).candidateCodes(candidateCodes).collectedSlots(new LinkedHashMap<>()).build();
            IntentRoutingSessionEntity session = createSession(
                    turn, loginUser, configuration, IntentClarificationType.INTENT_DISAMBIGUATION,
                    clarificationContext);
            return clarification(session, ambiguityQuestion(candidateCodes, configuration.getNodesByCode()),
                    candidateCodes, configuration.getNodesByCode());
        }
        // 步骤 6：意图明确，检查槽位 → 执行目标或缺槽位澄清
        String intentCode = topCandidate.intentCode();
        IntentNodeDefinition node = configuration.getNodesByCode().get(intentCode);
        if (Objects.isNull(node)) {
            return IntentRoutingResult.noMatch(query);
        }
        // 创建临时会话壳（不持久化，用于槽位检查的上下文传递）
        IntentRoutingSessionEntity transientSession = sessionShell(turn, loginUser, configuration, intentCode, query);
        return resolveSlotsOrTarget(transientSession, query, configuration, node);
    }

    /**
     * 意图确定后，检查该叶子的必填槽位。
     *
     * <ul>
     *   <li>无必填槽位 → 直接查找映射并返回执行目标</li>
     *   <li>有必填槽位 → 创建或更新 MISSING_SLOT 澄清会话，逐个提问</li>
     * </ul>
     *
     * @param session 临时会话壳（无槽位时）或已有的澄清会话（补槽位时）
     * @param query   原始查询文本（保留完整意图上下文）
     * @param configuration 已锁定的路由配置
     * @param node    确定的叶子意图节点
     * @return 执行目标结果 或 槽位澄清事件
     */
    private IntentRoutingResult resolveSlotsOrTarget(IntentRoutingSessionEntity session, String query,
                                                     RouteConfiguration configuration, IntentNodeDefinition node) {
        if (!hasMapping(configuration, node.getCode())) {
            return IntentRoutingResult.noMatch(query);
        }
        List<String> requiredSlots = requiredSlots(node);
        if (!requiredSlots.isEmpty()) {
            // 需要收集槽位：更新会话上下文，标记为 MISSING_SLOT 类型
            IntentClarificationContext context = session.getContext();
            if (CharSequenceUtil.isBlank(context.getOriginalQuery())) {
                context.setOriginalQuery(query);
            }
            context.setIntentCode(node.getCode());
            context.setMissingSlotCodes(requiredSlots);
            context.setCollectedSlots(Objects.requireNonNullElseGet(context.getCollectedSlots(), LinkedHashMap::new));
            session.setClarificationType(IntentClarificationType.MISSING_SLOT);
            session.setStatus(IntentRoutingSessionStatus.PENDING);
            session.setRoundNo(1);
            session.setExpiresAt(OffsetDateTime.now().plusMinutes(CLARIFICATION_TTL_MINUTES));
            // 临时壳无 ID → 插入新记录；已有会话 → 更新
            if (Objects.isNull(session.getId())) {
                sessionRepo.insert(session);
            } else {
                sessionRepo.update(session);
            }
            return slotClarification(session, node);
        }
        // 无必填槽位 → 直接返回执行目标
        return target(configuration, node.getCode(), query);
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
            return clarificationFailureResult(originalQuery);
        }
        // FIFO：取出第一个待收集的槽位
        String slotCode = missing.removeFirst();
        Map<String, String> collected = new LinkedHashMap<>(Objects.requireNonNullElse(context.getCollectedSlots(), Map.of()));
        collected.put(slotCode, value.trim());
        context.setCollectedSlots(collected);
        context.setMissingSlotCodes(missing);
        // 还有剩余槽位 → 检查轮次
        if (!missing.isEmpty()) {
            if (session.getRoundNo() >= MAX_CLARIFICATION_ROUNDS) {
                // 超轮次 → 失败兜底
                session.setStatus(IntentRoutingSessionStatus.FAILED);
                sessionRepo.update(session);
                return clarificationFailureResult(originalQuery);
            }
            // 轮次 +1，更新过期时间，继续提问下一个槽位
            session.setRoundNo(session.getRoundNo() + 1);
            session.setExpiresAt(OffsetDateTime.now().plusMinutes(CLARIFICATION_TTL_MINUTES));
            sessionRepo.update(session);
            return slotClarification(session, configuration.getNodesByCode().get(context.getIntentCode()));
        }
        // 所有槽位采集完成 → 拼接 enrichedQuery，返回执行目标
        session.setStatus(IntentRoutingSessionStatus.COMPLETED);
        sessionRepo.update(session);
        return target(configuration, context.getIntentCode(),
                enrichedQuery(originalQuery(context, originalQuery), collected));
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
            return clarificationFailureResult(session.getContext().getOriginalQuery());
        }
        session.setRoundNo(session.getRoundNo() + 1);
        session.setExpiresAt(OffsetDateTime.now().plusMinutes(CLARIFICATION_TTL_MINUTES));
        sessionRepo.update(session);
        List<String> codes = session.getContext().getCandidateCodes();
        return clarification(session, ambiguityQuestion(codes, configuration.getNodesByCode()),
                codes, configuration.getNodesByCode());
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
     * 创建一个不持久化的临时会话壳。
     *
     * <p>用于意图明确、无槽位场景下的上下文传递（如 resolveSlotsOrTarget 函数签名需要 session 参数）。
     * 仅在触发槽位澄清时才会被持久化（resolveSlotsOrTarget 中的 insert 分支）。</p>
     *
     * @param turn                对话轮次
     * @param loginUser           当前用户
     * @param configuration       已锁定的路由配置
     * @param intentCode          已确定的意图编码
     * @param originalQuery       原始用户查询
     * @return 未持久化的临时会话壳（id 为 null）
     */
    private static IntentRoutingSessionEntity sessionShell(ChatTurnBO turn, LoginUser loginUser,
                                                           RouteConfiguration configuration,
                                                           String intentCode, String originalQuery) {
        IntentRoutingSessionEntity session = new IntentRoutingSessionEntity();
        session.setConversationId(turn.conversationId());
        session.setUserId(loginUser.id());
        session.setApplicationVersionId(configuration.getApplication().versionId());
        session.setIntentTreeVersionId(configuration.getVersion().getId());
        session.setContext(IntentClarificationContext.builder()
                .originalQuery(originalQuery)
                .intentCode(intentCode)
                .collectedSlots(new LinkedHashMap<>())
                .build());
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
     * <p>如果找不到映射，抛出 BusinessException——这意味着发布校验环节有遗漏，
     * 属于系统错误而非用户错误。</p>
     *
     * @param configuration 已锁定的路由配置
     * @param intentCode 叶子意图编码
     * @param query      执行时使用的查询（原始请求或拼接了槽位的增强版）
     * @return 包含执行动作、映射和目标查询的结果
     */
    private static IntentRoutingResult target(RouteConfiguration configuration, String intentCode, String query) {
        Optional<ApplicationIntentMapping> resolvedMapping = configuration.getBinding().mappings().stream()
                .filter(item -> Objects.equals(item.getIntentCode(), intentCode))
                .findFirst();
        if (resolvedMapping.isEmpty()) {
            return IntentRoutingResult.noMatch(query);
        }
        ApplicationIntentMapping mapping = resolvedMapping.orElseThrow(() ->
                new BusinessException(HttpStatusEnum.CONFLICT, "意图缺少已发布执行映射：" + intentCode));
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

    private static IntentRoutingResult fallbackResult(String query) {
        return IntentRoutingResult.noMatch(query);
    }

    private static IntentRoutingResult clarificationFailureResult(String query) {
        return IntentRoutingResult.noMatch(query);
    }

    private static boolean hasMapping(RouteConfiguration configuration, String intentCode) {
        return configuration.getBinding().mappings().stream()
                .anyMatch(mapping -> Objects.equals(mapping.getIntentCode(), intentCode));
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
     * @return CLARIFICATION 动作的路由结果
     */
    private static IntentRoutingResult clarification(IntentRoutingSessionEntity session, String question, List<String> optionCodes, Map<String, IntentNodeDefinition> nodesByCode) {
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
    private static IntentRoutingResult slotClarification(IntentRoutingSessionEntity session, IntentNodeDefinition node) {
        String slotCode = session.getContext().getMissingSlotCodes().getFirst();
        String slotName = Optional.ofNullable(node.getSlots()).orElse(List.of()).stream()
                .filter(slot -> Objects.equals(slot.getCode(), slotCode))
                .map(IntentSlotDefinition::getName)
                .findFirst()
                .orElse(slotCode);
        String question = CharSequenceUtil.isNotBlank(node.getClarificationQuestion())
                ? node.getClarificationQuestion().trim()
                : "请补充\u201C" + slotName + "\u201D。";
        return clarification(session, question, List.of(), Map.of());
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

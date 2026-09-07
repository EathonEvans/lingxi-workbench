package com.lingxi.workbench.chat.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.application.runtime.model.vo.ApplicationAssistantIdentityVO;
import com.lingxi.workbench.application.runtime.service.app.ApplicationVersionService;
import com.lingxi.workbench.chat.common.enums.ConversationStatus;
import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.AssistantCompletionMetaBO;
import com.lingxi.workbench.chat.model.bo.ConversationCreateResult;
import com.lingxi.workbench.chat.ops.ConversationOpsMetrics;
import com.lingxi.workbench.chat.model.bo.ConversationTurnStartBO;
import com.lingxi.workbench.chat.model.bo.StartConversationTurnBO;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.chat.model.converter.ConversationConverter;
import com.lingxi.workbench.chat.model.entity.ConversationEntity;
import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import com.lingxi.workbench.chat.model.request.ConversationPageQuery;
import com.lingxi.workbench.chat.model.request.RenameConversationRequest;
import com.lingxi.workbench.chat.model.vo.ConversationMessageVO;
import com.lingxi.workbench.chat.model.vo.ConversationVO;
import com.lingxi.workbench.chat.repo.ConversationMessageRepo;
import com.lingxi.workbench.chat.repo.ConversationRepo;
import com.lingxi.workbench.chat.service.ConversationService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.PageQuery;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 用户会话及消息业务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final String DEFAULT_TITLE = "新会话";
    private static final int AUTO_TITLE_MAX_CODE_POINTS = 30;

    private final ConversationRepo conversationRepo;
    private final ConversationMessageRepo messageRepo;
    private final ConversationConverter converter;
    private final ConversationOpsMetrics opsMetrics;
    private final ApplicationVersionService appVersionService;

    /**
     * 分页查询当前用户的会话，支持关键字和状态过滤。
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<ConversationVO> page(Long userId, ConversationPageQuery query) {
        return conversationRepo.findPage(userId, query).map(converter::toVO);
    }

    /**
     * 分页查询指定会话的消息历史，先校验会话归属再按序号倒序返回。
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<ConversationMessageVO> messagePage(Long userId, String conversationId, PageQuery query) {
        Long persistedConversationId = parseConversationId(conversationId);
        requireConversation(persistedConversationId, userId);
        PageResult<ConversationMessageEntity> messages = messageRepo.findPage(persistedConversationId, query);
        List<Long> applicationVersionIds = messages.records().stream()
                .filter(message -> message.getRole() == MessageRole.ASSISTANT)
                .map(ConversationMessageEntity::getApplicationVersionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, ApplicationAssistantIdentityVO> assistantIdentities =
                appVersionService.resolveAssistantIdentities(applicationVersionIds);
        return messages.map(message -> toMessageVO(message, assistantIdentities));
    }

    private ConversationMessageVO toMessageVO(
            ConversationMessageEntity message,
            Map<Long, ApplicationAssistantIdentityVO> assistantIdentities) {
        ConversationMessageVO value = converter.toMessageVO(message);
        if (message.getRole() == MessageRole.ASSISTANT) {
            value.setAssistant(assistantIdentities.get(message.getApplicationVersionId()));
        }
        return value;
    }

    /**
     * 修改会话标题，校验用户归属后覆盖已有标题。
     */
    @Override
    @Transactional
    public void rename(Long userId, String conversationId, RenameConversationRequest request) {
        ConversationEntity entity = requireConversation(parseConversationId(conversationId), userId);
        entity.setTitle(request.title().trim());
        conversationRepo.save(entity);
    }

    /**
     * 归档会话，前提是当前没有正在生成中的回答。
     */
    @Override
    @Transactional
    public void archive(Long userId, String conversationId) {
        Long persistedConversationId = parseConversationId(conversationId);
        ConversationEntity entity = requireConversation(persistedConversationId, userId);
        requireNoGeneratingMessage(persistedConversationId);
        entity.setStatus(ConversationStatus.ARCHIVED);
        conversationRepo.save(entity);
    }

    /**
     * 软删除会话，设置删除时间标记，保留数据追溯能力。
     */
    @Override
    @Transactional
    public void delete(Long userId, String conversationId) {
        Long persistedConversationId = parseConversationId(conversationId);
        ConversationEntity entity = requireConversation(persistedConversationId, userId);
        requireNoGeneratingMessage(persistedConversationId);
        OffsetDateTime now = OffsetDateTime.now();
        entity.setDeletedAt(now);
        conversationRepo.save(entity);
    }

    /**
     * 在同一事务中创建本轮用户消息和 Assistant 占位消息。
     *
     * <p>该方法锁定用户所属会话，校验会话可写、无其他生成中回答，
     * 并使用客户端消息标识防止重复提交。返回后两条消息均已落库。</p>
     */
    @Override
    @Transactional
    public ConversationTurnStartBO startTurn(StartConversationTurnBO input) {
        String prompt = input.getPrompt().trim();
        // 新会话在首次发送事务内幂等创建；已有会话继续使用主键锁串行化消息序号。
        ConversationEntity conversation = resolveConversation(input);
        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "该会话已归档");
        }

        // clientMessageId 由前端为一次发送动作生成，网络重试时仍携带同一值，从而避免重复落库和重复扣费。
        Optional<ChatTurnBO> existingTurn = findExistingTurn(conversation, input, prompt);
        if (existingTurn.isPresent()) {
            return new ConversationTurnStartBO(existingTurn.get(), false);
        }

        // 当前版本仍保持同一会话单任务执行；后续数据库队列阶段再将新消息持久化为 QUEUED。
        requireNoGeneratingMessage(conversation.getId());

        OffsetDateTime now = OffsetDateTime.now();

        // 当前最大序号在会话锁保护下递增；用户消息使用奇偶不做强约束，只保证同一会话内严格有序。
        int userSequence = messageRepo.nextSequence(conversation.getId());

        // 用户输入在请求到达时已经完整，因此直接以 COMPLETED 状态持久化。
        ConversationMessageEntity userMessage = newMessage(conversation.getId(), input.getClientMessageId(), userSequence, MessageRole.USER, prompt, MessageStatus.COMPLETED, null);
        userMessage.setCompletedAt(now);
        messageRepo.save(userMessage);

        // 在调用模型前先创建空的 Assistant 占位消息。后续完整回答、失败内容和 Token 用量都更新这条记录。
        ConversationMessageEntity assistantMessage = newMessage(conversation.getId(), null, userSequence + 1, MessageRole.ASSISTANT, "", MessageStatus.GENERATING, input.getModelInstanceId());
        assistantMessage.setApplicationId(input.getApplicationId());
        assistantMessage.setApplicationVersionId(input.getApplicationVersionId());
        messageRepo.save(assistantMessage);

        // 第一次提问自动生成会话标题，之后由用户主动重命名，避免每轮回答都覆盖已有标题。
        if (conversation.getMessageCount() == 0) {
            conversation.setTitle(createTitle(prompt));
        }

        // 一轮固定新增用户和 Assistant 两条消息；lastMessageAt 使用用户消息提交时间。
        conversation.setMessageCount(conversation.getMessageCount() + 2);
        conversation.setLastMessageAt(now);
        conversationRepo.save(conversation);
        // Assistant 消息 ID 会作为后续模型生成任务 ID，用户消息序号用于排除本轮消息后查询历史记忆。
        ChatTurnBO turn = new ChatTurnBO(conversation.getId(), userMessage.getId(), assistantMessage.getId(), userSequence);
        return new ConversationTurnStartBO(turn, true);
    }

    /**
     * 将 Assistant 消息更新为完整回答，记录内容、Token 用量并标记完成。
     */
    @Override
    @Transactional
    public void completeAssistant(
            Long assistantMessageId,
            String content,
            TokenUsage tokenUsage,
            AssistantCompletionMetaBO metadata) {
        boolean completed = messageRepo.finish(
                assistantMessageId,
                MessageStatus.COMPLETED,
                content,
                metadata.code(),
                truncate(metadata.message(), 255),
                tokenUsage,
                metadata.modelInstanceId(),
                OffsetDateTime.now());
        if (!completed && findCurrentStatus(assistantMessageId) != MessageStatus.COMPLETED) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Assistant消息已进入其他终态");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MessageStatus> findAssistantCompletionStatus(Long conversationId, Long assistantMessageId, Long applicationVersionId) {
        return messageRepo.findCompletionStatus(conversationId, assistantMessageId, applicationVersionId);
    }

    /**
     * 记录模型调用失败，保留已生成的文本片段和错误信息。
     */
    @Override
    @Transactional
    public void failAssistant(Long assistantMessageId, String content, String errorCode, String errorMessage, TokenUsage tokenUsage) {
        messageRepo.finish(assistantMessageId, MessageStatus.FAILED, content, errorCode, truncate(errorMessage, 255), tokenUsage, null, OffsetDateTime.now());
    }

    /**
     * 记录客户端主动取消生成，保留已生成部分并标记取消原因。
     */
    @Override
    @Transactional
    public boolean cancelAssistant(Long assistantMessageId, String content, TokenUsage tokenUsage) {
        boolean cancelled = messageRepo.finish(assistantMessageId, MessageStatus.CANCELLED, content,
                "CLIENT_CANCELLED", "客户端中断了本次回答", tokenUsage, null, OffsetDateTime.now());
        return cancelled || findCurrentStatus(assistantMessageId) == MessageStatus.CANCELLED;
    }

    /**
     * 记录服务进程异常中断导致未完成的回答。
     */
    @Override
    @Transactional
    public void interruptAssistant(Long assistantMessageId, String content, TokenUsage tokenUsage) {
        messageRepo.finish(assistantMessageId, MessageStatus.INTERRUPTED, content, "GENERATION_INTERRUPTED", "模型生成任务已中断", tokenUsage, null, OffsetDateTime.now());
    }

    @Override
    @Transactional
    public void waitForHuman(Long assistantMessageId, String content, TokenUsage tokenUsage) {
        if (!messageRepo.waitForHuman(assistantMessageId, content, tokenUsage)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Assistant消息无法进入等待人工决策状态");
        }
    }

    @Override
    @Transactional
    public void resumeFromHuman(Long assistantMessageId) {
        if (!messageRepo.resumeFromHuman(assistantMessageId)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Assistant消息已被其他恢复任务领取");
        }
    }

    @Override
    @Transactional
    public void cancelWaiting(Long assistantMessageId, String code, String message) {
        if (!messageRepo.cancelWaiting(assistantMessageId, code, truncate(message, 255), OffsetDateTime.now())) {
            MessageStatus status = findCurrentStatus(assistantMessageId);
            if (status == MessageStatus.GENERATING || status == MessageStatus.WAITING_HUMAN) {
                throw new BusinessException(HttpStatusEnum.CONFLICT, "Assistant消息等待状态已变化");
            }
        }
    }

    /**
     * 查询并校验会话归属，不存在或不属于当前用户时抛出 NOT_FOUND 异常。
     */
    private ConversationEntity requireConversation(Long conversationId, Long userId) {
        return conversationRepo.findOwnedById(conversationId, userId).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "会话不存在"));
    }

    /**
     * 根据服务端会话主键解析并锁定本轮所属会话；未传主键则创建新会话。
     */
    private ConversationEntity resolveConversation(StartConversationTurnBO input) {
        if (CharSequenceUtil.isNotBlank(input.getConversationId())) {
            return conversationRepo.lockOwnedById(parseConversationId(input.getConversationId()), input.getUserId())
                    .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "会话不存在"));
        }
        ConversationCreateResult result = conversationRepo.createAndLockForFirstMessage(
                input.getUserId(), input.getClientMessageId(), DEFAULT_TITLE);
        if (!result.created()) {
            opsMetrics.recordLazyCreateConflict();
        }
        return result.conversation();
    }

    /** 将对外的字符串会话 ID 转换为持久化主键。 */
    private static Long parseConversationId(String conversationId) {
        try {
            return Long.valueOf(conversationId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "会话ID格式不正确");
        }
    }

    /**
     * 命中相同客户端消息标识时返回原轮次，并校验重试请求没有篡改业务输入。
     */
    private Optional<ChatTurnBO> findExistingTurn(ConversationEntity conversation, StartConversationTurnBO input, String prompt) {
        Optional<ConversationMessageEntity> optionalUserMessage = messageRepo.findUserMessage(conversation.getId(), input.getClientMessageId());
        if (optionalUserMessage.isEmpty()) {
            return Optional.empty();
        }
        ConversationMessageEntity userMessage = optionalUserMessage.get();
        if (!Objects.equals(userMessage.getContent(), prompt)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "客户端消息ID对应的用户输入不一致");
        }
        ConversationMessageEntity assistantMessage = messageRepo.findBySequence(conversation.getId(), userMessage.getSequenceNo() + 1)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "客户端消息ID对应的Assistant消息不存在"));
        if (assistantMessage.getRole() != MessageRole.ASSISTANT
                || !Objects.equals(assistantMessage.getApplicationId(), input.getApplicationId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "客户端消息ID对应的Application归属不一致");
        }
        return Optional.of(new ChatTurnBO(conversation.getId(), userMessage.getId(), assistantMessage.getId(), userMessage.getSequenceNo()));
    }

    /**
     * 读取 Assistant 当前状态，供幂等终态写入区分重复完成与并发覆盖。
     */
    private MessageStatus findCurrentStatus(Long assistantMessageId) {
        return messageRepo.findById(assistantMessageId)
                .map(ConversationMessageEntity::getStatus)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "Assistant消息不存在"));
    }

    /**
     * 校验当前会话没有正在生成中的回答，防止并发产生消息顺序交叉。
     */
    private void requireNoGeneratingMessage(Long conversationId) {
        if (messageRepo.existsGenerating(conversationId)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "当前会话仍有回答正在生成");
        }
    }

    /**
     * 构建未持久化的消息实体，createdAt 由框架填充器维护。
     */
    private static ConversationMessageEntity newMessage(Long conversationId, String clientMessageId, int sequenceNo, MessageRole role, String content, MessageStatus status, Long modelInstanceId) {
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setConversationId(conversationId);
        entity.setClientMessageId(clientMessageId);
        entity.setSequenceNo(sequenceNo);
        entity.setRole(role);
        entity.setContent(content);
        entity.setStatus(status);
        entity.setModelInstanceId(modelInstanceId);
        return entity;
    }

    /**
     * 从用户首条消息截取不超过 30 个 Unicode 码点的文本作为会话标题。
     */
    private static String createTitle(String prompt) {
        String normalized = prompt.trim().replaceAll("\\s+", " ");
        int codePointCount = normalized.codePointCount(0, normalized.length());
        if (codePointCount <= AUTO_TITLE_MAX_CODE_POINTS) {
            return normalized;
        }
        int endIndex = normalized.offsetByCodePoints(0, AUTO_TITLE_MAX_CODE_POINTS);
        return normalized.substring(0, endIndex) + "…";
    }

    /**
     * 截断字符串到指定长度，防止过长错误信息超出数据库字段限制。
     */
    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.chat.common.enums.ConversationStatus;
import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 用户会话及消息业务实现。
 */
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final String DEFAULT_TITLE = "新会话";
    private static final int AUTO_TITLE_MAX_CODE_POINTS = 30;

    private final ConversationRepo conversationRepo;
    private final ConversationMessageRepo messageRepo;
    private final ConversationConverter converter;

    /**
     * 创建新的空会话，初始标题为"新会话"，状态为 ACTIVE。
     */
    @Override
    @Transactional
    public ConversationVO create(Long userId) {
        ConversationEntity entity = new ConversationEntity();
        entity.setUserId(userId);
        entity.setTitle(DEFAULT_TITLE);
        entity.setStatus(ConversationStatus.ACTIVE);
        entity.setMessageCount(0);
        conversationRepo.save(entity);
        return converter.toVO(entity);
    }

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
    public PageResult<ConversationMessageVO> messagePage(Long userId, Long conversationId, PageQuery query) {
        requireConversation(conversationId, userId);
        return messageRepo.findPage(conversationId, query).map(converter::toMessageVO);
    }

    /**
     * 修改会话标题，校验用户归属后覆盖已有标题。
     */
    @Override
    @Transactional
    public void rename(Long userId, Long conversationId, RenameConversationRequest request) {
        ConversationEntity entity = requireConversation(conversationId, userId);
        entity.setTitle(request.title().trim());
        conversationRepo.save(entity);
    }

    /**
     * 归档会话，前提是当前没有正在生成中的回答。
     */
    @Override
    @Transactional
    public void archive(Long userId, Long conversationId) {
        ConversationEntity entity = requireConversation(conversationId, userId);
        requireNoGeneratingMessage(conversationId);
        entity.setStatus(ConversationStatus.ARCHIVED);
        conversationRepo.save(entity);
    }

    /**
     * 软删除会话，设置删除时间标记，保留数据追溯能力。
     */
    @Override
    @Transactional
    public void delete(Long userId, Long conversationId) {
        ConversationEntity entity = requireConversation(conversationId, userId);
        requireNoGeneratingMessage(conversationId);
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
    public ChatTurnBO startTurn(Long userId, Long conversationId, String clientMessageId, String prompt, Long modelInstanceId, Long applicationId, Long applicationVersionId) {
        // 悲观锁串行化同一会话的发送请求，避免两个请求同时计算出相同的消息序号。
        ConversationEntity conversation = conversationRepo.lockOwnedById(conversationId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "会话不存在"));
        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "已归档会话不能继续发送消息");
        }

        // 第一版约束同一会话同一时间只允许一个 Assistant 回答生成，防止消息顺序和上下文发生交叉。
        requireNoGeneratingMessage(conversationId);

        // clientMessageId 由前端为一次发送动作生成，网络重试时仍携带同一值，从而避免重复落库和重复扣费。
        if (messageRepo.existsClientMessageId(conversationId, clientMessageId)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "该消息已经提交，请勿重复发送");
        }

        OffsetDateTime now = OffsetDateTime.now();

        // 当前最大序号在会话锁保护下递增；用户消息使用奇偶不做强约束，只保证同一会话内严格有序。
        int userSequence = messageRepo.nextSequence(conversationId);

        // 用户输入在请求到达时已经完整，因此直接以 COMPLETED 状态持久化。
        ConversationMessageEntity userMessage = newMessage(conversationId, clientMessageId, userSequence, MessageRole.USER, prompt.trim(), MessageStatus.COMPLETED, null);
        userMessage.setCompletedAt(now);
        messageRepo.save(userMessage);

        // 在调用模型前先创建空的 Assistant 占位消息。后续完整回答、失败内容和 Token 用量都更新这条记录。
        ConversationMessageEntity assistantMessage = newMessage(conversationId, null, userSequence + 1, MessageRole.ASSISTANT, "", MessageStatus.GENERATING, modelInstanceId);
        assistantMessage.setApplicationId(applicationId);
        assistantMessage.setApplicationVersionId(applicationVersionId);
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
        return new ChatTurnBO(conversationId, userMessage.getId(), assistantMessage.getId(), userSequence);
    }

    /**
     * 将 Assistant 消息更新为完整回答，记录内容、Token 用量并标记完成。
     */
    @Override
    @Transactional
    public void completeAssistant(Long assistantMessageId, String content, TokenUsage tokenUsage) {
        messageRepo.finish(assistantMessageId, MessageStatus.COMPLETED, content, null, null, tokenUsage, OffsetDateTime.now());
    }

    /**
     * 记录模型调用失败，保留已生成的文本片段和错误信息。
     */
    @Override
    @Transactional
    public void failAssistant(Long assistantMessageId, String content, String errorCode, String errorMessage, TokenUsage tokenUsage) {
        messageRepo.finish(assistantMessageId, MessageStatus.FAILED, content, errorCode, truncate(errorMessage, 255), tokenUsage, OffsetDateTime.now());
    }

    /**
     * 记录客户端主动取消生成，保留已生成部分并标记取消原因。
     */
    @Override
    @Transactional
    public void cancelAssistant(Long assistantMessageId, String content, TokenUsage tokenUsage) {
        messageRepo.finish(assistantMessageId, MessageStatus.CANCELLED, content, "CLIENT_CANCELLED", "客户端中断了本次回答", tokenUsage, OffsetDateTime.now());
    }

    /**
     * 记录服务进程异常中断导致未完成的回答。
     */
    @Override
    @Transactional
    public void interruptAssistant(Long assistantMessageId, String content, TokenUsage tokenUsage) {
        messageRepo.finish(assistantMessageId, MessageStatus.INTERRUPTED, content, "GENERATION_INTERRUPTED", "模型生成任务已中断", tokenUsage, OffsetDateTime.now());
    }

    /**
     * 查询并校验会话归属，不存在或不属于当前用户时抛出 NOT_FOUND 异常。
     */
    private ConversationEntity requireConversation(Long conversationId, Long userId) {
        return conversationRepo.findOwnedById(conversationId, userId).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "会话不存在"));
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

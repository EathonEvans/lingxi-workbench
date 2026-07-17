package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.chat.common.enums.ConversationStatus;
import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.MessageTokenUsageBO;
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

    @Override
    @Transactional(readOnly = true)
    public PageResult<ConversationVO> page(Long userId, ConversationPageQuery query) {
        return conversationRepo.findPage(userId, query).map(converter::toVO);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ConversationMessageVO> messagePage(Long userId, Long conversationId, PageQuery query) {
        requireConversation(conversationId, userId);
        return messageRepo.findPage(conversationId, query).map(converter::toMessageVO);
    }

    @Override
    @Transactional
    public void rename(Long userId, Long conversationId, RenameConversationRequest request) {
        ConversationEntity entity = requireConversation(conversationId, userId);
        entity.setTitle(request.title().trim());
        conversationRepo.save(entity);
    }

    @Override
    @Transactional
    public void archive(Long userId, Long conversationId) {
        ConversationEntity entity = requireConversation(conversationId, userId);
        requireNoGeneratingMessage(conversationId);
        entity.setStatus(ConversationStatus.ARCHIVED);
        conversationRepo.save(entity);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long conversationId) {
        ConversationEntity entity = requireConversation(conversationId, userId);
        requireNoGeneratingMessage(conversationId);
        OffsetDateTime now = OffsetDateTime.now();
        entity.setDeletedAt(now);
        conversationRepo.save(entity);
    }

    @Override
    @Transactional
    public ChatTurnBO startTurn(Long userId, Long conversationId, String clientMessageId, String prompt, Long modelInstanceId) {
        ConversationEntity conversation = conversationRepo.lockOwnedById(conversationId, userId).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "会话不存在"));
        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "已归档会话不能继续发送消息");
        }
        requireNoGeneratingMessage(conversationId);
        if (messageRepo.existsClientMessageId(conversationId, clientMessageId)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "该消息已经提交，请勿重复发送");
        }

        OffsetDateTime now = OffsetDateTime.now();
        int userSequence = messageRepo.nextSequence(conversationId);
        ConversationMessageEntity userMessage = newMessage(
                conversationId,
                clientMessageId,
                userSequence,
                MessageRole.USER,
                prompt.trim(),
                MessageStatus.COMPLETED,
                null);
        userMessage.setCompletedAt(now);
        messageRepo.save(userMessage);

        ConversationMessageEntity assistantMessage = newMessage(
                conversationId,
                null,
                userSequence + 1,
                MessageRole.ASSISTANT,
                "",
                MessageStatus.GENERATING,
                modelInstanceId);
        messageRepo.save(assistantMessage);

        if (conversation.getMessageCount() == 0) {
            conversation.setTitle(createTitle(prompt));
        }
        conversation.setMessageCount(conversation.getMessageCount() + 2);
        conversation.setLastMessageAt(now);
        conversationRepo.save(conversation);
        return new ChatTurnBO(conversationId, userMessage.getId(), assistantMessage.getId(), userSequence);
    }

    @Override
    @Transactional
    public void completeAssistant(
            Long assistantMessageId, String content, MessageTokenUsageBO tokenUsage) {
        messageRepo.finish(
                assistantMessageId,
                MessageStatus.COMPLETED,
                content,
                null,
                null,
                tokenUsage,
                OffsetDateTime.now());
    }

    @Override
    @Transactional
    public void failAssistant(
            Long assistantMessageId,
            String content,
            String errorCode,
            String errorMessage,
            MessageTokenUsageBO tokenUsage) {
        messageRepo.finish(
                assistantMessageId,
                MessageStatus.FAILED,
                content,
                errorCode,
                truncate(errorMessage, 255),
                tokenUsage,
                OffsetDateTime.now());
    }

    @Override
    @Transactional
    public void cancelAssistant(
            Long assistantMessageId, String content, MessageTokenUsageBO tokenUsage) {
        messageRepo.finish(
                assistantMessageId,
                MessageStatus.CANCELLED,
                content,
                "CLIENT_CANCELLED",
                "客户端中断了本次回答",
                tokenUsage,
                OffsetDateTime.now());
    }

    @Override
    @Transactional
    public void interruptAssistant(
            Long assistantMessageId, String content, MessageTokenUsageBO tokenUsage) {
        messageRepo.finish(
                assistantMessageId,
                MessageStatus.INTERRUPTED,
                content,
                "GENERATION_INTERRUPTED",
                "模型生成任务已中断",
                tokenUsage,
                OffsetDateTime.now());
    }

    private ConversationEntity requireConversation(Long conversationId, Long userId) {
        return conversationRepo.findOwnedById(conversationId, userId).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "会话不存在"));
    }

    private void requireNoGeneratingMessage(Long conversationId) {
        if (messageRepo.existsGenerating(conversationId)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "当前会话仍有回答正在生成");
        }
    }

    private static ConversationMessageEntity newMessage(
            Long conversationId,
            String clientMessageId,
            int sequenceNo,
            MessageRole role,
            String content,
            MessageStatus status,
            Long modelInstanceId) {
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

    private static String createTitle(String prompt) {
        String normalized = prompt.trim().replaceAll("\\s+", " ");
        int codePointCount = normalized.codePointCount(0, normalized.length());
        if (codePointCount <= AUTO_TITLE_MAX_CODE_POINTS) {
            return normalized;
        }
        int endIndex = normalized.offsetByCodePoints(0, AUTO_TITLE_MAX_CODE_POINTS);
        return normalized.substring(0, endIndex) + "…";
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

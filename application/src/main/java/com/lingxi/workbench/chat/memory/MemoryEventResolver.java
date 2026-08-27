package com.lingxi.workbench.chat.memory;

import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.app.ApplicationVersionService;
import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.memory.event.AssistantCompletedEvent;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.entity.ConversationEntity;
import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import com.lingxi.workbench.chat.model.message.ConversationCompletedMessage;
import com.lingxi.workbench.chat.repo.ConversationMessageRepo;
import com.lingxi.workbench.chat.repo.ConversationRepo;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 根据事务消息中的稳定标识重建记忆处理所需的冻结业务上下文。
 */
@Service
@RequiredArgsConstructor
public class MemoryEventResolver {

    private final ConversationRepo conversationRepo;
    private final ConversationMessageRepo messageRepo;
    private final ApplicationVersionService applicationVersionService;

    @Transactional(readOnly = true)
    public AssistantCompletedEvent resolve(ConversationCompletedMessage message) {
        ConversationEntity conversation = conversationRepo.findById(message.conversationId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "记忆事件对应会话不存在"));

        ConversationMessageEntity assistantMessage = messageRepo.findCompletedAssistant(
                        message.conversationId(), message.assistantMessageId(), message.appVersionId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "记忆事件对应Assistant消息未完成"));

        ConversationMessageEntity userMessage = messageRepo.findBySequence(
                        message.conversationId(), assistantMessage.getSequenceNo() - 1)
                .filter(candidate -> candidate.getRole() == MessageRole.USER)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "记忆事件对应用户消息不存在"));

        if (Objects.isNull(assistantMessage.getApplicationId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "记忆事件对应Assistant消息缺少Application主键");
        }

        AppRuntimeSnapshot application = applicationVersionService.loadRuntimeAppVersion(
                assistantMessage.getApplicationId(), message.appVersionId());
        ChatTurnBO turn = new ChatTurnBO(
                message.conversationId(), userMessage.getId(), assistantMessage.getId(), userMessage.getSequenceNo());
        return new AssistantCompletedEvent(
                conversation.getUserId(), turn, message.appVersionId(), application.definition().memoryConf());
    }
}

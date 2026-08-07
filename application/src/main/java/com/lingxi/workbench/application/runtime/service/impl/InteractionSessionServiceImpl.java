package com.lingxi.workbench.application.runtime.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionStatus;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.model.ApplicationRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.InteractionSessionContext;
import com.lingxi.workbench.application.runtime.model.InteractionSessionOpenCommand;
import com.lingxi.workbench.application.runtime.model.PlannerInteractionResolution;
import com.lingxi.workbench.application.runtime.model.entity.InteractionSessionEntity;
import com.lingxi.workbench.application.runtime.repo.InteractionSessionRepo;
import com.lingxi.workbench.application.runtime.service.InteractionSessionService;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.request.ClarificationResponseRequest;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 通用运行时交互会话实现。 */
@Service
@RequiredArgsConstructor
public class InteractionSessionServiceImpl implements InteractionSessionService {

    private static final int EXPIRATION_MINUTES = 10;

    private final InteractionSessionRepo repo;

    @Override
    @Transactional
    public ChatClarificationEventBO open(InteractionSessionOpenCommand command) {
        requireOpenCommand(command);
        cancelPending(command);
        OffsetDateTime now = OffsetDateTime.now();
        InteractionSessionEntity session = new InteractionSessionEntity();
        session.setConversationId(command.getTurn().conversationId());
        session.setUserId(command.getUserId());
        session.setApplicationVersionId(command.getApplication().applicationVersionId());
        session.setRunId(command.getRunId());
        session.setInteractionType(command.getInteractionType());
        session.setStatus(InteractionSessionStatus.PENDING);
        session.setRoundNo(1);
        session.setContext(command.getContext());
        session.setExpiresAt(now.plusMinutes(EXPIRATION_MINUTES));
        repo.insert(session);
        return toEvent(session);
    }

    @Override
    @Transactional
    public Optional<PlannerInteractionResolution> resolve(ApplicationRuntimeSnapshot application, ChatTurnBO turn,
                                                          Long userId, String query,
                                                          ClarificationResponseRequest response) {
        if (!isPlannerResponse(response)) {
            return Optional.empty();
        }
        InteractionSessionEntity session = findSession(application, turn, userId, response);
        if (session.getExpiresAt().isBefore(OffsetDateTime.now())) {
            session.setStatus(InteractionSessionStatus.EXPIRED);
            repo.update(session);
            throw new BusinessException(HttpStatusEnum.CONFLICT, "交互会话已过期，请重新发起任务");
        }
        String suppliedValue = resolveValue(session, query, response);
        if (session.getInteractionType() == InteractionSessionType.RISK_CONFIRMATION
                && !"确认执行".equals(suppliedValue)) {
            session.setStatus(InteractionSessionStatus.CANCELLED);
            repo.update(session);
            throw new BusinessException(HttpStatusEnum.CONFLICT, "高风险操作已取消");
        }
        session.getContext().setResponse(suppliedValue);
        session.setStatus(InteractionSessionStatus.RESOLVED);
        repo.update(session);
        return Optional.of(new PlannerInteractionResolution(
                session.getContext().getOriginalQuery(), suppliedValue, session.getInteractionType(),
                session.getInteractionType() == InteractionSessionType.RISK_CONFIRMATION
                        ? Set.copyOf(session.getContext().capabilityCodes())
                        : Set.of()));
    }

    private void cancelPending(InteractionSessionOpenCommand command) {
        repo.findPending(command.getTurn().conversationId(), command.getUserId(),
                        command.getApplication().applicationVersionId())
                .ifPresent(session -> {
                    session.setStatus(InteractionSessionStatus.CANCELLED);
                    repo.update(session);
                });
    }

    private InteractionSessionEntity findSession(ApplicationRuntimeSnapshot application, ChatTurnBO turn, Long userId,
                                                  ClarificationResponseRequest response) {
        InteractionSessionEntity session;
        if (CharSequenceUtil.isNotBlank(response.getClarificationId())) {
            long sessionId;
            try {
                sessionId = Long.parseLong(response.getClarificationId());
            } catch (NumberFormatException exception) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "澄清会话ID格式不正确");
            }
            session = repo.findOwned(sessionId, turn.conversationId(), userId)
                    .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "交互会话不存在"));
        } else {
            session = repo.findPending(turn.conversationId(), userId, application.applicationVersionId())
                    .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "待处理交互会话不存在"));
        }
        if (!Objects.equals(session.getApplicationVersionId(), application.applicationVersionId())
                || session.getStatus() != InteractionSessionStatus.PENDING
                || !session.getInteractionType().getCode().equals(response.getInteractionType())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "交互会话状态或版本不匹配");
        }
        return session;
    }

    private static String resolveValue(InteractionSessionEntity session, String query,
                                       ClarificationResponseRequest response) {
        String optionCode = response.getOptionCode();
        if (CharSequenceUtil.isNotBlank(optionCode)) {
            int optionIndex;
            try {
                optionIndex = Integer.parseInt(optionCode) - 1;
            } catch (NumberFormatException exception) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "澄清选项不存在");
            }
            List<String> options = session.getContext().getClarification().options();
            if (optionIndex < 0 || optionIndex >= options.size()) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "澄清选项不存在");
            }
            return options.get(optionIndex);
        }
        String value = CharSequenceUtil.isNotBlank(response.getValue()) ? response.getValue() : query;
        if (value == null || value.isBlank()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "澄清响应不能为空");
        }
        return value.trim();
    }

    private static ChatClarificationEventBO toEvent(InteractionSessionEntity session) {
        List<ChatClarificationEventBO.ClarificationOptionBO> options = new ArrayList<>();
        List<String> labels = session.getContext().getClarification().options();
        for (int index = 0; index < labels.size(); index++) {
            options.add(new ChatClarificationEventBO.ClarificationOptionBO(String.valueOf(index + 1), labels.get(index)));
        }
        return ChatClarificationEventBO.builder()
                .clarificationId(String.valueOf(session.getId()))
                .interactionType(session.getInteractionType().getCode())
                .question(session.getContext().getClarification().getQuestion())
                .options(List.copyOf(options))
                .round(session.getRoundNo())
                .expiresAt(session.getExpiresAt())
                .build();
    }

    private static boolean isPlannerResponse(ClarificationResponseRequest response) {
        if (response == null || response.getInteractionType() == null) {
            return false;
        }
        return InteractionSessionType.PLANNER_CLARIFICATION.getCode().equals(response.getInteractionType())
                || InteractionSessionType.RISK_CONFIRMATION.getCode().equals(response.getInteractionType());
    }

    private static void requireOpenCommand(InteractionSessionOpenCommand command) {
        InteractionSessionContext context = command == null ? null : command.getContext();
        if (command == null || command.getApplication() == null || command.getTurn() == null
                || command.getUserId() == null || command.getRunId() == null || command.getRunId().isBlank()
                || command.getInteractionType() == null || context == null || context.getClarification() == null
                || context.getOriginalQuery() == null || context.getOriginalQuery().isBlank()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "交互会话参数不完整");
        }
    }
}

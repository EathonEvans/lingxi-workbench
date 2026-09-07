package com.lingxi.workbench.application.runtime.service.routing;

import com.lingxi.workbench.application.runtime.common.enums.IntentClarificationType;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingReason;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.IntentClarificationContext;
import com.lingxi.workbench.application.runtime.model.IntentRoutingEvidence;
import com.lingxi.workbench.application.runtime.model.IntentRoutingResult;
import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingDecisionEntity;
import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingSessionEntity;
import com.lingxi.workbench.application.runtime.repo.IntentRoutingDecisionRepo;
import com.lingxi.workbench.application.runtime.repo.IntentRoutingSessionRepo;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IntentRoutingTxServiceImplTest {
    private final IntentRoutingDecisionRepo decisionRepo = mock(IntentRoutingDecisionRepo.class);
    private final IntentRoutingSessionRepo sessionRepo = mock(IntentRoutingSessionRepo.class);
    private final IntentRoutingTxServiceImpl service = new IntentRoutingTxServiceImpl(decisionRepo, sessionRepo);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    private IntentRoutingTxServiceImpl transactionalService;

    @BeforeEach
    void prepareTransactionProxy() {
        when(transactions.getTransaction(any())).thenReturn(transaction);
        ProxyFactory factory = new ProxyFactory(service);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        transactionalService = (IntentRoutingTxServiceImpl) factory.getProxy();
        doAnswer(call -> { call.<IntentRoutingDecisionEntity>getArgument(0).setId(900L); return null; }).when(decisionRepo).insert(any());
    }

    @Test
    void shouldCommitInitialSessionWithDecisionAndReturnAssignedId() {
        IntentRoutingSessionEntity session = session(null);
        doAnswer(call -> { call.<IntentRoutingSessionEntity>getArgument(0).setId(99L); return null; }).when(sessionRepo).insert(session);
        IntentRoutingResult result = transactionalService.append(context(), null, outcome(session));
        assertThat(result.getDecisionId()).isEqualTo(900L);
        assertThat(result.getClarification().getClarificationId()).isEqualTo("99");
        assertThat(session.getContext().getDecisionId()).isEqualTo(900L);
        assertThat(session.getContext().getOriginalMessageId()).isEqualTo(11L);
        verify(sessionRepo).insert(session);
        verify(sessionRepo, never()).updatePending(any(), any(), anyInt());
        verify(transactions).commit(transaction);
    }

    @Test
    void shouldKeepInitialReferenceAndUpdateOnlyExpectedPendingProgress() {
        IntentRoutingSessionEntity session = session(99L);
        session.getContext().setDecisionId(800L);
        session.getContext().setOriginalMessageId(8L);
        transactionalService.append(context(), null, outcome(session));
        assertThat(session.getContext().getDecisionId()).isEqualTo(800L);
        assertThat(session.getContext().getOriginalMessageId()).isEqualTo(8L);
        verify(sessionRepo).updatePending(session, IntentClarificationType.INTENT_DISAMBIGUATION, 2);
        verify(transactions).commit(transaction);
    }

    @Test
    void shouldRollbackDecisionWhenReplyAlreadyAdvancedSession() {
        IntentRoutingSessionEntity session = session(99L);
        doThrow(new BusinessException(HttpStatusEnum.CONFLICT, "澄清会话已经推进"))
                .when(sessionRepo).updatePending(session, IntentClarificationType.INTENT_DISAMBIGUATION, 2);
        assertThatThrownBy(() -> transactionalService.append(context(), null, outcome(session)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已经推进");
        verify(decisionRepo).insert(any());
        verify(transactions).rollback(transaction);
        verify(transactions, never()).commit(any());
    }

    @Test
    void shouldRollbackDecisionWhenNewSessionCannotBeSaved() {
        IntentRoutingSessionEntity session = session(null);
        doThrow(new IllegalStateException("database unavailable")).when(sessionRepo).insert(session);
        assertThatThrownBy(() -> transactionalService.append(context(), null, outcome(session))).hasMessage("database unavailable");
        verify(transactions).rollback(transaction);
        verify(transactions, never()).commit(any());
    }

    private static IntentRoutingOutcome outcome(IntentRoutingSessionEntity session) {
        IntentRoutingResult result = IntentRoutingResult.builder().action(IntentRoutingAction.CLARIFICATION)
                .clarification(ChatClarificationEventBO.builder().question("请选择").options(List.of()).build())
                .routingEvidence(IntentRoutingEvidence.builder().reason(IntentRoutingReason.SEMANTIC_CLARIFICATION).candidates(List.of()).build()).build();
        return IntentRoutingOutcome.builder().result(result).session(session)
                .expectedState(new IntentRoutingOutcome.PendingState(IntentClarificationType.INTENT_DISAMBIGUATION, 2)).build();
    }

    private static IntentRoutingSessionEntity session(Long id) {
        IntentRoutingSessionEntity session = new IntentRoutingSessionEntity();
        session.setId(id);
        session.setContext(IntentClarificationContext.builder().originalQuery("退款").build());
        return session;
    }

    private static ConversationContext context() {
        AppRuntimeSnapshot application = mock(AppRuntimeSnapshot.class);
        when(application.applicationId()).thenReturn(1L);
        when(application.versionId()).thenReturn(30L);
        ConversationContext context = mock(ConversationContext.class);
        when(context.getApplicationSnapshot()).thenReturn(application);
        when(context.getTurn()).thenReturn(new ChatTurnBO(10L, 11L, 12L, 1));
        when(context.getLoginUser()).thenReturn(new LoginUser(7L, "tester", "测试用户", null));
        return context;
    }
}

package com.lingxi.workbench.application.runtime.service.routing;

import com.lingxi.workbench.application.runtime.model.IntentClarificationContext;
import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingDecisionEntity;
import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingSessionEntity;
import com.lingxi.workbench.application.runtime.repo.IntentRoutingDecisionRepo;
import com.lingxi.workbench.application.runtime.repo.IntentRoutingSessionRepo;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntentRoutingDecisionPersistenceServiceImplTest {

    private final IntentRoutingDecisionRepo decisionRepo = mock(IntentRoutingDecisionRepo.class);
    private final IntentRoutingSessionRepo sessionRepo = mock(IntentRoutingSessionRepo.class);
    private final IntentRoutingDecisionPersistenceServiceImpl service =
            new IntentRoutingDecisionPersistenceServiceImpl(decisionRepo, sessionRepo);

    @Test
    void shouldAppendDecisionAndLinkInitialClarificationContext() {
        IntentRoutingDecisionEntity decision = new IntentRoutingDecisionEntity();
        decision.setConversationId(10L);
        decision.setUserId(7L);
        decision.setUserMessageId(11L);
        doAnswer(invocation -> {
            IntentRoutingDecisionEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            return null;
        }).when(decisionRepo).insert(decision);
        IntentRoutingSessionEntity session = new IntentRoutingSessionEntity();
        session.setId(99L);
        session.setContext(IntentClarificationContext.builder().originalQuery("退款").build());
        when(sessionRepo.findOwned(99L, 10L, 7L)).thenReturn(Optional.of(session));

        Long decisionId = service.append(decision, 99L);

        assertThat(decisionId).isEqualTo(900L);
        assertThat(session.getContext().getDecisionId()).isEqualTo(900L);
        assertThat(session.getContext().getOriginalMessageId()).isEqualTo(11L);
        verify(sessionRepo).update(session);
    }

    @Test
    void shouldKeepInitialDecisionReferenceWhenClarificationContinues() {
        IntentRoutingDecisionEntity decision = new IntentRoutingDecisionEntity();
        decision.setId(901L);
        decision.setConversationId(10L);
        decision.setUserId(7L);
        decision.setUserMessageId(13L);
        IntentRoutingSessionEntity session = new IntentRoutingSessionEntity();
        session.setId(99L);
        session.setContext(IntentClarificationContext.builder()
                .decisionId(900L)
                .originalMessageId(11L)
                .build());
        when(sessionRepo.findOwned(99L, 10L, 7L)).thenReturn(Optional.of(session));

        service.append(decision, 99L);

        assertThat(session.getContext().getDecisionId()).isEqualTo(900L);
        assertThat(session.getContext().getOriginalMessageId()).isEqualTo(11L);
    }
}

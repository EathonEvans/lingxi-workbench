package com.lingxi.workbench.application.runtime.service.interaction;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionStatus;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.InteractionSessionContext;
import com.lingxi.workbench.application.runtime.model.InteractionSessionOpenCommand;
import com.lingxi.workbench.application.runtime.model.PlannerClarification;
import com.lingxi.workbench.application.runtime.model.InteractionResolution;
import com.lingxi.workbench.application.runtime.model.entity.InteractionSessionEntity;
import com.lingxi.workbench.application.runtime.repo.InteractionSessionRepo;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.request.ClarificationResponseRequest;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.FallbackAgent;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.CapabilityRiskLevel;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionSessionServiceImplTest {

    @Test
    void shouldResolveOwnedRiskConfirmationWithPublishedVersion() {
        InMemoryInteractionSessionRepo repo = new InMemoryInteractionSessionRepo();
        InteractionSessionServiceImpl service = new InteractionSessionServiceImpl(repo);
        ChatTurnBO turn = new ChatTurnBO(10L, 11L, 12L, 1);
        ChatClarificationEventBO event = service.open(openCommand(application(20L), turn, 7L));
        ClarificationResponseRequest response = response(event, "1");

        InteractionResolution resolution = service.resolve(application(20L), turn, 7L, "确认", response).orElseThrow();

        assertEquals("执行退款", resolution.getOriginalQuery());
        assertEquals("确认执行", resolution.getResponse());
        assertEquals(InteractionSessionType.RISK_CONFIRMATION, resolution.getType());
        assertEquals(java.util.Set.of("refund_write"), resolution.confirmedCapabilityCodes());
        assertEquals("default_agent", resolution.getResumeTarget().capabilityCode());
        assertEquals(InteractionSessionStatus.RESOLVED, repo.session().getStatus());
    }

    @Test
    void shouldRejectWrongOwnerVersionAndExpiredSession() {
        ChatTurnBO turn = new ChatTurnBO(10L, 11L, 12L, 1);

        InMemoryInteractionSessionRepo ownerRepo = new InMemoryInteractionSessionRepo();
        InteractionSessionServiceImpl ownerService = new InteractionSessionServiceImpl(ownerRepo);
        ChatClarificationEventBO ownerEvent = ownerService.open(openCommand(application(20L), turn, 7L));
        assertThrows(BusinessException.class, () -> ownerService.resolve(
                application(20L), turn, 8L, "确认", response(ownerEvent, "1")));

        InMemoryInteractionSessionRepo versionRepo = new InMemoryInteractionSessionRepo();
        InteractionSessionServiceImpl versionService = new InteractionSessionServiceImpl(versionRepo);
        ChatClarificationEventBO versionEvent = versionService.open(openCommand(application(20L), turn, 7L));
        assertThrows(BusinessException.class, () -> versionService.resolve(
                application(21L), turn, 7L, "确认", response(versionEvent, "1")));

        InMemoryInteractionSessionRepo expiredRepo = new InMemoryInteractionSessionRepo();
        InteractionSessionServiceImpl expiredService = new InteractionSessionServiceImpl(expiredRepo);
        ChatClarificationEventBO expiredEvent = expiredService.open(openCommand(application(20L), turn, 7L));
        expiredRepo.session().setExpiresAt(OffsetDateTime.now().minusSeconds(1));
        assertThrows(BusinessException.class, () -> expiredService.resolve(
                application(20L), turn, 7L, "确认", response(expiredEvent, "1")));
        assertEquals(InteractionSessionStatus.EXPIRED, expiredRepo.session().getStatus());
    }

    @Test
    void shouldCancelRiskOperationWhenPlatformOwnedCancelOptionIsSelected() {
        InMemoryInteractionSessionRepo repo = new InMemoryInteractionSessionRepo();
        InteractionSessionServiceImpl service = new InteractionSessionServiceImpl(repo);
        ChatTurnBO turn = new ChatTurnBO(10L, 11L, 12L, 1);
        ChatClarificationEventBO event = service.open(openCommand(application(20L), turn, 7L));

        assertThrows(BusinessException.class, () -> service.resolve(
                application(20L), turn, 7L, "取消", response(event, "2")));

        assertEquals(InteractionSessionStatus.CANCELLED, repo.session().getStatus());
    }

    private static InteractionSessionOpenCommand openCommand(AppRuntimeSnapshot application, ChatTurnBO turn, Long userId) {
        PlannerClarification clarification = PlannerClarification.builder()
                .question("确认执行退款操作吗？")
                .options(List.of("确认执行", "取消"))
                .interactionType(InteractionSessionType.RISK_CONFIRMATION)
                .capabilityCodes(List.of("refund_write"))
                .build();
        return InteractionSessionOpenCommand.builder()
                .application(application)
                .turn(turn)
                .userId(userId)
                .runId("run-1")
                .resumeCapabilityCode("default_agent")
                .orchestratorAgentId(1L)
                .orchestratorAgentVersionId(2L)
                .interactionType(InteractionSessionType.RISK_CONFIRMATION)
                .context(InteractionSessionContext.builder().clarification(clarification)
                        .capabilityCodes(List.of("refund_write")).originalQuery("执行退款").build())
                .build();
    }

    private static ClarificationResponseRequest response(ChatClarificationEventBO event, String optionCode) {
        ClarificationResponseRequest response = new ClarificationResponseRequest();
        response.setClarificationId(event.getClarificationId());
        response.setInteractionType(event.getInteractionType());
        response.setOptionCode(optionCode);
        return response;
    }

    private static AppRuntimeSnapshot application(Long versionId) {
        ApplicationCapabilityDefinition fallbackAgent = ApplicationCapabilityDefinition.builder()
                .code("default_agent")
                .name("默认助手")
                .type(ApplicationCapabilityType.AGENT)
                .targetId(1L)
                .targetVersionId(2L)
                .inputSchema(new JSONObject())
                .outputSchema(new JSONObject())
                .riskLevel(CapabilityRiskLevel.READ_ONLY)
                .timeoutSeconds(10)
                .maxCalls(1)
                .resultTokenBudget(1000)
                .build();
        ApplicationDefinition definition = ApplicationDefinition.builder()
                .schemaVersion(com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION)
                .fallback(new FallbackAgent("default_agent"))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder().capabilities(List.of(fallbackAgent)).build())
                .build();
        return new AppRuntimeSnapshot(1L, "risk-app", versionId, definition);
    }

    private static final class InMemoryInteractionSessionRepo extends InteractionSessionRepo {
        private final List<InteractionSessionEntity> sessions = new ArrayList<>();

        private InMemoryInteractionSessionRepo() {
            super(null);
        }

        @Override
        public void insert(InteractionSessionEntity session) {
            session.setId((long) sessions.size() + 1);
            sessions.add(session);
        }

        @Override
        public void update(InteractionSessionEntity session) {
        }

        @Override
        public Optional<InteractionSessionEntity> findOwned(Long id, Long conversationId, Long userId) {
            return sessions.stream().filter(session -> session.getId().equals(id)
                    && session.getConversationId().equals(conversationId)
                    && session.getUserId().equals(userId)).findFirst();
        }

        @Override
        public Optional<InteractionSessionEntity> findPending(Long conversationId, Long userId, Long applicationVersionId) {
            return sessions.stream().filter(session -> session.getConversationId().equals(conversationId)
                    && session.getUserId().equals(userId)
                    && session.getApplicationVersionId().equals(applicationVersionId)
                    && session.getStatus() == InteractionSessionStatus.PENDING).findFirst();
        }

        private InteractionSessionEntity session() {
            assertTrue(!sessions.isEmpty());
            return sessions.getLast();
        }
    }
}

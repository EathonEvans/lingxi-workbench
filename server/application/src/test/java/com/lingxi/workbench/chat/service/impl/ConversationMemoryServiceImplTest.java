package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.config.ChatMemoryProperties;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.ConversationMemorySnapshot;
import com.lingxi.workbench.chat.model.bo.ConversationSummarySource;
import com.lingxi.workbench.chat.model.bo.ConversationSummaryTrigger;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import com.lingxi.workbench.chat.model.entity.ConversationEntity;
import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import com.lingxi.workbench.chat.repo.ConversationMessageRepo;
import com.lingxi.workbench.chat.repo.ConversationRepo;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationMemoryServiceImplTest {

    @Test
    void shouldKeepLatestCompleteRoundWithinTokenBudget() {
        ConversationRepo conversationRepo = mock(ConversationRepo.class);
        ConversationMessageRepo messageRepo = mock(ConversationMessageRepo.class);
        TokenCountEstimator estimator = mock(TokenCountEstimator.class);
        ChatMemoryProperties properties = new ChatMemoryProperties();
        properties.setMaxRounds(10);
        properties.setMaxTokens(30);
        when(conversationRepo.findOwnedById(10L, 7L))
                .thenReturn(Optional.of(new ConversationEntity()));
        when(messageRepo.findRecentCompletedRounds(10L, 5, 10)).thenReturn(List.of(
                message(MessageRole.USER, "第一问"),
                message(MessageRole.ASSISTANT, "第一答"),
                message(MessageRole.USER, "第二问"),
                message(MessageRole.ASSISTANT, "第二答")
        ));
        when(estimator.estimate(anyString())).thenReturn(5);
        ConversationMemoryServiceImpl service = new ConversationMemoryServiceImpl(
                conversationRepo, messageRepo, properties, estimator);

        List<ChatMemoryMessageBO> result = service.getWindowMemory(7L, 10L, 5);

        assertEquals(List.of(
                new ChatMemoryMessageBO(MessageRole.USER, "第二问"),
                new ChatMemoryMessageBO(MessageRole.ASSISTANT, "第二答")
        ), result);
    }

    @Test
    void shouldLoadSummaryAndExcludeCoveredRoundsWithOneOwnershipCheck() {
        ConversationRepo conversationRepo = mock(ConversationRepo.class);
        ConversationMessageRepo messageRepo = mock(ConversationMessageRepo.class);
        TokenCountEstimator estimator = mock(TokenCountEstimator.class);
        ChatMemoryProperties properties = new ChatMemoryProperties();
        ConversationEntity conversation = new ConversationEntity();
        conversation.setSummary(" 已有摘要 ");
        conversation.setSummarySequenceNo(3);
        when(conversationRepo.findOwnedById(10L, 7L)).thenReturn(Optional.of(conversation));
        when(messageRepo.findRecentCompletedRounds(10L, 7, 10)).thenReturn(List.of(
                message(1, MessageRole.USER, "第一问"),
                message(2, MessageRole.ASSISTANT, "第一答"),
                message(3, MessageRole.USER, "第二问"),
                message(4, MessageRole.ASSISTANT, "第二答"),
                message(5, MessageRole.USER, "第三问"),
                message(6, MessageRole.ASSISTANT, "第三答")));
        when(estimator.estimate(anyString())).thenReturn(1);
        ConversationMemoryServiceImpl service = new ConversationMemoryServiceImpl(conversationRepo, messageRepo, properties, estimator);
        ContextBudgetPolicy budget = ContextBudgetPolicy.builder().maxHistoryRounds(10).maxSystemPromptTokens(1000).maxCapabilityTokens(1000)
                .maxHistoryTokens(1000).maxMemoryTokens(1000).maxInputTokens(1000).maxObservationTokens(1000)
                .reservedOutputTokens(1000).build();

        ConversationMemorySnapshot snapshot = service.loadContextMemory(7L, new ChatTurnBO(10L, 11L, 12L, 7), budget);

        assertEquals("已有摘要", snapshot.summary());
        assertEquals(List.of(
                new ChatMemoryMessageBO(5, MessageRole.USER, "第三问"),
                new ChatMemoryMessageBO(6, MessageRole.ASSISTANT, "第三答")), snapshot.recentMessages());
        verify(conversationRepo).findOwnedById(10L, 7L);
    }

    @Test
    void shouldLoadEarliestUnsummarizedRoundsForRollingSummary() {
        ConversationRepo conversationRepo = mock(ConversationRepo.class);
        ConversationMessageRepo messageRepo = mock(ConversationMessageRepo.class);
        TokenCountEstimator estimator = mock(TokenCountEstimator.class);
        ConversationEntity conversation = new ConversationEntity();
        conversation.setSummary("前序摘要");
        conversation.setSummarySequenceNo(3);
        when(conversationRepo.findOwnedById(10L, 7L)).thenReturn(Optional.of(conversation));
        when(messageRepo.findCompletedRoundsAfter(10L, 3, 9, 2)).thenReturn(List.of(
                message(5, MessageRole.USER, "第三问"),
                message(6, MessageRole.ASSISTANT, "第三答"),
                message(7, MessageRole.USER, "第四问"),
                message(8, MessageRole.ASSISTANT, "第四答")));
        when(estimator.estimate(anyString())).thenReturn(1);
        ConversationMemoryServiceImpl service = new ConversationMemoryServiceImpl(
                conversationRepo, messageRepo, new ChatMemoryProperties(), estimator);

        Optional<ConversationSummarySource> source = service.loadSummarySource(
                7L, new ChatTurnBO(10L, 11L, 12L, 9), new ConversationSummaryTrigger(2, 100, 100));

        assertEquals("前序摘要", source.orElseThrow().previousSummary());
        assertEquals(3, source.orElseThrow().previousCoveredUserSequenceNo());
        assertEquals(7, source.orElseThrow().coveredUserSequenceNo());
        assertEquals(4, source.orElseThrow().messages().size());
    }

    @Test
    void shouldNotPrepareSummaryBeforeEnoughNewRoundsComplete() {
        ConversationRepo conversationRepo = mock(ConversationRepo.class);
        ConversationMessageRepo messageRepo = mock(ConversationMessageRepo.class);
        TokenCountEstimator estimator = mock(TokenCountEstimator.class);
        when(conversationRepo.findOwnedById(10L, 7L)).thenReturn(Optional.of(new ConversationEntity()));
        when(messageRepo.findCompletedRoundsAfter(10L, 0, 3, 2)).thenReturn(List.of(
                message(1, MessageRole.USER, "第一问"),
                message(2, MessageRole.ASSISTANT, "第一答")));
        ConversationMemoryServiceImpl service = new ConversationMemoryServiceImpl(
                conversationRepo, messageRepo, new ChatMemoryProperties(), estimator);

        Optional<ConversationSummarySource> source = service.loadSummarySource(
                7L, new ChatTurnBO(10L, 11L, 12L, 3), new ConversationSummaryTrigger(2, 100, 100));

        assertEquals(Optional.empty(), source);
    }

    @Test
    void shouldPrepareSummaryWhenTokenThresholdIsReachedBeforeRoundThreshold() {
        ConversationRepo conversationRepo = mock(ConversationRepo.class);
        ConversationMessageRepo messageRepo = mock(ConversationMessageRepo.class);
        TokenCountEstimator estimator = mock(TokenCountEstimator.class);
        when(conversationRepo.findOwnedById(10L, 7L)).thenReturn(Optional.of(new ConversationEntity()));
        when(messageRepo.findCompletedRoundsAfter(10L, 0, 3, 4)).thenReturn(List.of(
                message(1, MessageRole.USER, "较长问题"),
                message(2, MessageRole.ASSISTANT, "较长回答")));
        when(estimator.estimate(anyString())).thenReturn(20);
        ConversationMemoryServiceImpl service = new ConversationMemoryServiceImpl(
                conversationRepo, messageRepo, new ChatMemoryProperties(), estimator);

        Optional<ConversationSummarySource> source = service.loadSummarySource(
                7L, new ChatTurnBO(10L, 11L, 12L, 3), new ConversationSummaryTrigger(4, 50, 100));

        assertEquals(2, source.orElseThrow().messages().size());
    }

    private static ConversationMessageEntity message(MessageRole role, String content) {
        return message(null, role, content);
    }

    private static ConversationMessageEntity message(Integer sequenceNo, MessageRole role, String content) {
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setSequenceNo(sequenceNo);
        entity.setRole(role);
        entity.setContent(content);
        return entity;
    }
}

package com.lingxi.workbench.chat.memory;

import com.lingxi.workbench.chat.memory.event.AssistantCompletedEvent;
import com.lingxi.workbench.chat.memory.model.entity.UserProfileEntity;
import com.lingxi.workbench.chat.memory.repo.UserProfileRepo;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import com.lingxi.workbench.workflow.model.UserProfileMemoryConfig;
import com.lingxi.workbench.workflow.model.UserProfileUpdateStrategy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProfileMemoryServiceTest {

    @Test
    void shouldMergeExtractedTopLevelFieldsWithExistingProfile() {
        UserProfileRepo profileRepo = mock(UserProfileRepo.class);
        MemoryCompletionService completionService = mock(MemoryCompletionService.class);
        UserProfileEntity existing = new UserProfileEntity();
        existing.setProfile(Map.of("language", "zh-CN", "city", "上海"));
        when(profileRepo.findByUserId(7L)).thenReturn(Optional.of(existing));
        when(completionService.complete(
                org.mockito.ArgumentMatchers.eq(30L), org.mockito.ArgumentMatchers.eq(21L),
                org.mockito.ArgumentMatchers.anyMap())).thenReturn("{\"city\":\"杭州\"}");
        UserProfileMemoryService service = new UserProfileMemoryService(completionService, profileRepo);

        service.extract(event(), "USER: 我搬到杭州了", config(UserProfileUpdateStrategy.MERGE));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> profileCaptor = ArgumentCaptor.forClass(Map.class);
        verify(profileRepo).upsert(eq(7L), eq(12L), profileCaptor.capture());
        assertThat(profileCaptor.getValue()).containsEntry("language", "zh-CN").containsEntry("city", "杭州");
    }

    @Test
    void shouldReplaceExistingProfileWhenConfigured() {
        UserProfileRepo profileRepo = mock(UserProfileRepo.class);
        MemoryCompletionService completionService = mock(MemoryCompletionService.class);
        UserProfileEntity existing = new UserProfileEntity();
        existing.setProfile(Map.of("language", "zh-CN", "city", "上海"));
        when(profileRepo.findByUserId(7L)).thenReturn(Optional.of(existing));
        when(completionService.complete(
                org.mockito.ArgumentMatchers.eq(30L), org.mockito.ArgumentMatchers.eq(21L),
                org.mockito.ArgumentMatchers.anyMap())).thenReturn("{\"city\":\"杭州\"}");
        UserProfileMemoryService service = new UserProfileMemoryService(completionService, profileRepo);

        service.extract(event(), "USER: 我搬到杭州了", config(UserProfileUpdateStrategy.REPLACE));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> profileCaptor = ArgumentCaptor.forClass(Map.class);
        verify(profileRepo).upsert(eq(7L), eq(12L), profileCaptor.capture());
        assertThat(profileCaptor.getValue()).isEqualTo(Map.of("city", "杭州"));
    }

    private static AssistantCompletedEvent event() {
        return new AssistantCompletedEvent(
                7L, new ChatTurnBO(10L, 11L, 12L, 8), 40L, MemoryConfig.disabled());
    }

    private static MemoryConfig config(UserProfileUpdateStrategy strategy) {
        return MemoryConfig.builder()
                .chatModelInstanceId(21L)
                .profile(UserProfileMemoryConfig.builder()
                        .enabled(true)
                        .promptVersionId(30L)
                        .updateStrategy(strategy)
                        .build())
                .build();
    }
}

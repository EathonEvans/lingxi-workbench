package com.lingxi.workbench.chat.repo;

import com.alibaba.fastjson2.JSON;
import com.lingxi.workbench.chat.config.ChatGenerationEventProperties;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventCacheBO;
import com.lingxi.workbench.framework.cache.RedisUtil;
import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionStatus;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatGenerationEventRepoTest {

    private static final String EVENT_KEY = "lx_wbc:chat:generation:event:12";

    @Test
    void shouldCacheSafeToolEventWithLimitAndTtl() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        ChatGenerationEventProperties properties = properties();
        ChatGenerationEventRepo repo = new ChatGenerationEventRepo(redisUtil, properties);
        ChatGenerationEventBO event = ChatGenerationEventBO.tool(2, toolEvent());

        repo.append(12L, event);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisUtil).lRightPush(eq(EVENT_KEY), valueCaptor.capture());
        verify(redisUtil).lTrim(EVENT_KEY, -2000, -1);
        verify(redisUtil).expire(EVENT_KEY, 24, TimeUnit.HOURS);
        ChatGenerationEventCacheBO cached = JSON.parseObject(valueCaptor.getValue(), ChatGenerationEventCacheBO.class);
        assertEquals("system_current_time", cached.getToolCode());
        assertEquals(AgentToolExecutionStatus.SUCCEEDED, cached.getToolStatus());
    }

    @Test
    void shouldRestoreOnlyEventsAfterRequestedSequence() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        ChatGenerationEventRepo repo = new ChatGenerationEventRepo(redisUtil, properties());
        String first = JSON.toJSONString(ChatGenerationEventCacheBO.from(ChatGenerationEventBO.chunk(1, "旧")));
        String second = JSON.toJSONString(ChatGenerationEventCacheBO.from(ChatGenerationEventBO.tool(2, toolEvent())));
        when(redisUtil.lRange(EVENT_KEY, 0, -1)).thenReturn(List.of(first, second));

        List<ChatGenerationEventBO> events = repo.findAfter(12L, 1);

        assertEquals(1, events.size());
        assertEquals(2, events.getFirst().sequence());
        assertEquals(AgentToolExecutionStatus.SUCCEEDED, events.getFirst().tool().getStatus());
    }

    private static ChatGenerationEventProperties properties() {
        ChatGenerationEventProperties properties = new ChatGenerationEventProperties();
        properties.setMaxEvents(2000);
        properties.setTtlHours(24);
        return properties;
    }

    private static AgentToolExecutionEvent toolEvent() {
        return AgentToolExecutionEvent.builder()
                .invocationId("tool-1")
                .toolCode("system_current_time")
                .displayName("查询当前时间")
                .status(AgentToolExecutionStatus.SUCCEEDED)
                .durationMillis(18L)
                .build();
    }
}

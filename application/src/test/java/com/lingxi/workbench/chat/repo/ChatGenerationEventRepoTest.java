package com.lingxi.workbench.chat.repo;

import com.alibaba.fastjson2.JSON;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.chat.config.ChatGenerationEventProperties;
import com.lingxi.workbench.chat.common.enums.ChatGenerationEventType;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventCacheBO;
import com.lingxi.workbench.chat.model.bo.ChatPlanStepBO;
import com.lingxi.workbench.chat.model.bo.ChatStepOutputEventBO;
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

    @Test
    void shouldRoundTripExecutionTimelineThroughReplayCache() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        ChatGenerationEventRepo repo = new ChatGenerationEventRepo(redisUtil, properties());
        ChatExecutionTimelineEventBO timeline = ChatExecutionTimelineEventBO.builder()
                .runId("run-1")
                .eventType(ExecutionEventType.STEP_STARTED)
                .planNo(1)
                .stepId("collect")
                .displayName("收集资料")
                .stepStatus(ExecutionStepStatus.RUNNING)
                .steps(List.of(new ChatPlanStepBO("collect", "收集资料", "support_agent")))
                .build();
        ChatGenerationEventBO source = ChatGenerationEventBO.execution(
                3, com.lingxi.workbench.chat.common.enums.ChatGenerationEventType.STEP, timeline);
        when(redisUtil.lRange(EVENT_KEY, 0, -1)).thenReturn(List.of(
                JSON.toJSONString(ChatGenerationEventCacheBO.from(source))));

        ChatGenerationEventBO restored = repo.findAfter(12L, 2).getFirst();

        assertEquals(3, restored.sequence());
        assertEquals("run-1", restored.execution().getRunId());
        assertEquals(ExecutionEventType.STEP_STARTED, restored.execution().getEventType());
        assertEquals("收集资料", restored.execution().getDisplayName());
        assertEquals(ExecutionStepStatus.RUNNING, restored.execution().getStepStatus());
        assertEquals("collect", restored.execution().steps().getFirst().stepId());
        assertEquals("support_agent", restored.execution().steps().getFirst().capabilityCode());
    }

    @Test
    void shouldRoundTripStepOutputThroughReplayCache() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        ChatGenerationEventRepo repo = new ChatGenerationEventRepo(redisUtil, properties());
        ChatGenerationEventBO source = ChatGenerationEventBO.stepOutput(
                4, new ChatStepOutputEventBO("run-1", 2, "collect", "已获取资料"));
        when(redisUtil.lRange(EVENT_KEY, 0, -1)).thenReturn(List.of(
                JSON.toJSONString(ChatGenerationEventCacheBO.from(source))));

        ChatGenerationEventBO restored = repo.findAfter(12L, 3).getFirst();

        assertEquals(ChatGenerationEventType.STEP_OUTPUT, restored.type());
        assertEquals("run-1", restored.stepOutput().runId());
        assertEquals(2, restored.stepOutput().planNo());
        assertEquals("collect", restored.stepOutput().stepId());
        assertEquals("已获取资料", restored.stepOutput().content());
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

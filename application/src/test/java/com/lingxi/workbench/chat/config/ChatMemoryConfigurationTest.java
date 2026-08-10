package com.lingxi.workbench.chat.config;

import com.lingxi.workbench.chat.memory.InteractionRecordMemoryService;
import com.lingxi.workbench.chat.memory.MemoryPostProcessor;
import com.lingxi.workbench.chat.memory.ConversationSummaryMemoryService;
import com.lingxi.workbench.chat.memory.UserProfileMemoryService;
import com.lingxi.workbench.chat.service.ConversationMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ChatMemoryConfigurationTest {

    @Test
    void shouldApplyAsyncProxyToMemoryPostProcessor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(ChatMemoryConfiguration.class, MemoryPostProcessor.class);
            context.registerBean(ConversationMemoryService.class, () -> mock(ConversationMemoryService.class));
            context.registerBean(ConversationSummaryMemoryService.class, () -> mock(ConversationSummaryMemoryService.class));
            context.registerBean(UserProfileMemoryService.class, () -> mock(UserProfileMemoryService.class));
            context.registerBean(InteractionRecordMemoryService.class, () -> mock(InteractionRecordMemoryService.class));
            context.refresh();

            assertTrue(AopUtils.isAopProxy(context.getBean(MemoryPostProcessor.class)));
        }
    }
}

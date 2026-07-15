package com.lingxi.workbench.chat.controller;

import com.lingxi.workbench.auth.config.UserSaTokenAuthWebConfig;
import com.lingxi.workbench.auth.config.interceptor.UserAuthenticationInterceptor;
import com.lingxi.workbench.chat.model.vo.ChatChunkVO;
import com.lingxi.workbench.chat.service.ChatStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = ChatStreamController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = UserSaTokenAuthWebConfig.class
        )
)
class ChatStreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatStreamService chatStreamService;

    @MockitoBean
    private UserAuthenticationInterceptor userAuthenticationInterceptor;

    @Test
    void shouldWriteMessageAndDoneSseEvents() throws Exception {
        when(chatStreamService.stream(any())).thenReturn(Flux.just(
                new ChatChunkVO("你"),
                new ChatChunkVO("好")
        ));

        MvcResult result = mockMvc.perform(post("/api/v1/ai/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"prompt\":\"你好\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult completedResult = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        String content = new String(completedResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(content.contains("event:message"));
        assertTrue(content.contains("data:{\"content\":\"你\"}"));
        assertTrue(content.contains("data:{\"content\":\"好\"}"));
        assertTrue(content.contains("event:done"));
    }

    @Test
    void shouldConvertModelFailureToSafeSseErrorEvent() throws Exception {
        when(chatStreamService.stream(any())).thenReturn(Flux.error(new RuntimeException("provider-secret-error")));

        MvcResult result = mockMvc.perform(post("/api/v1/ai/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"prompt\":\"你好\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult completedResult = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andReturn();
        String content = new String(completedResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(content.contains("event:error"));
        assertTrue(content.contains("AI_MODEL_ERROR"));
        assertFalse(content.contains("provider-secret-error"));
        assertFalse(content.contains("event:done"));
    }

    @Test
    void shouldRejectBlankPromptBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"prompt\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatStreamService);
    }
}

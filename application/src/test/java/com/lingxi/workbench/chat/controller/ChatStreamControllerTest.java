package com.lingxi.workbench.chat.controller;

import com.lingxi.workbench.auth.config.UserSaTokenAuthWebConfig;
import com.lingxi.workbench.auth.config.interceptor.UserAuthenticationInterceptor;
import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.vo.ChatStartVO;
import com.lingxi.workbench.chat.service.ChatStreamService;
import com.lingxi.workbench.framework.context.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
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

    @BeforeEach
    void setUserContext() {
        UserContext.set(new LoginUser(1L, "tester", "测试用户", null));
    }

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void shouldCreateGenerationTaskBeforeSseSubscription() throws Exception {
        when(chatStreamService.start(any(), eq(1L)))
                .thenReturn(new ChatStartVO("10", "11", "12"));

        mockMvc.perform(post("/api/v1/ai/chat/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"10","clientMessageId":"client-start","prompt":"你好"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "code": 200,
                          "data": {
                            "assistantMessageId": "12",
                            "conversationId": "10",
                            "userMessageId": "11"
                          },
                          "msg": "成功"
                        }
                        """));
    }

    @Test
    void shouldConvertModelFailureToSafeSseErrorEvent() throws Exception {
        when(chatStreamService.resume(12L, 0, 1L))
                .thenReturn(Flux.just(ChatGenerationEventBO.failed()));

        MvcResult result = mockMvc.perform(post("/api/v1/ai/chat/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {"assistantMessageId":"12","afterSequence":0}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult completedResult = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andReturn();
        String content = new String(completedResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(content.contains("event:error"));
        assertTrue(content.contains("AI_MODEL_ERROR"));
    }

    @Test
    void shouldResumeGenerationAfterSpecifiedSequence() throws Exception {
        when(chatStreamService.resume(12L, 3, 1L)).thenReturn(Flux.just(
                ChatGenerationEventBO.chunk(4, "继续"),
                ChatGenerationEventBO.completed()));

        MvcResult result = mockMvc.perform(post("/api/v1/ai/chat/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {"assistantMessageId":"12","afterSequence":3}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult completedResult = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andReturn();
        String content = new String(
                completedResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(content.contains("id:4"));
        assertTrue(content.contains("data:{\"content\":\"继续\",\"sequence\":4}"));
        assertTrue(content.contains("event:done"));
    }

    @Test
    void shouldCancelGenerationExplicitly() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assistantMessageId":"12"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"code":200,"msg":"成功"}
                        """));

        verify(chatStreamService).cancel(12L, 1L);
    }

    @Test
    void shouldRejectBlankPromptBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"10","clientMessageId":"client-003","prompt":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"code":400,"msg":"请求参数不合法"}
                        """));

        verifyNoInteractions(chatStreamService);
    }
}

package com.lingxi.workbench.chat.controller;

import com.lingxi.workbench.auth.config.UserSaTokenAuthWebConfig;
import com.lingxi.workbench.auth.config.interceptor.UserAuthenticationInterceptor;
import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.chat.common.enums.ConversationStatus;
import com.lingxi.workbench.chat.model.request.ConversationPageQuery;
import com.lingxi.workbench.chat.model.vo.ConversationMessageVO;
import com.lingxi.workbench.chat.model.vo.ConversationVO;
import com.lingxi.workbench.chat.service.ConversationService;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.convention.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 应用端会话接口测试。
 */
@WebMvcTest(
        value = ConversationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = UserSaTokenAuthWebConfig.class
        )
)
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

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
    void shouldBindConversationSearchAndReturnStringId() throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-16T20:00:00+08:00");
        ConversationVO conversation = ConversationVO.builder()
                .id("2077574496283791361")
                .title("市场分析")
                .status(ConversationStatus.ARCHIVED)
                .messageCount(6)
                .lastMessageAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        when(conversationService.page(eq(1L), any()))
                .thenReturn(new PageResult<>(List.of(conversation), 2, 10, 11));

        mockMvc.perform(get("/api/v1/conversations/list")
                        .param("current", "2")
                        .param("size", "10")
                        .param("keyword", "市场")
                        .param("status", "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "code": 200,
                          "data": {
                            "current": 2,
                            "size": 10,
                            "total": 11,
                            "records": [{
                              "id": "2077574496283791361",
                              "title": "市场分析",
                              "status": "ARCHIVED",
                              "messageCount": 6
                            }]
                          }
                        }
                        """));

        ArgumentCaptor<ConversationPageQuery> queryCaptor =
                ArgumentCaptor.forClass(ConversationPageQuery.class);
        verify(conversationService).page(eq(1L), queryCaptor.capture());
        assertEquals(2, queryCaptor.getValue().getCurrent());
        assertEquals(10, queryCaptor.getValue().getSize());
        assertEquals("市场", queryCaptor.getValue().getKeyword());
        assertEquals(ConversationStatus.ARCHIVED, queryCaptor.getValue().getStatus());
    }

    @Test
    void shouldPassStringConversationIdToMessageService() throws Exception {
        when(conversationService.messagePage(eq(1L), eq("2077574496283791361"), any()))
                .thenReturn(new PageResult<ConversationMessageVO>(List.of(), 1, 10, 0));

        mockMvc.perform(get("/api/v1/conversations/message-list")
                        .param("conversationId", "2077574496283791361")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());

        verify(conversationService).messagePage(eq(1L), eq("2077574496283791361"), any());
    }
}

package com.lingxi.workbench.framework.web.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.AsyncContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiRequestLoggingFilterTest {

    private final ApiRequestLoggingFilter filter = new ApiRequestLoggingFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ApiRequestLoggingFilter.class);
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void shouldLogJsonBodyQueryParameterAndResponseWithSensitiveValuesMasked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setParameter("source", "web");
        request.setContent("{\"username\":\"lingxi\",\"password\":\"plain-password\"}"
                .getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (wrappedRequest, wrappedResponse) -> {
            wrappedRequest.getInputStream().readAllBytes();
            wrappedResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            wrappedResponse.getWriter().write("{\"code\":200,\"data\":{\"accessToken\":\"secret-token\"}}");
        });

        assertEquals("{\"code\":200,\"data\":{\"accessToken\":\"secret-token\"}}",
                response.getContentAsString());
        String message = singleLogMessage();
        assertTrue(message.contains("请求接口地址 uri：/api/v1/auth/login"));
        assertTrue(message.contains("\"source\":\"web\""));
        assertTrue(message.contains("\"username\":\"lingxi\""));
        assertFalse(message.contains("plain-password"));
        assertFalse(message.contains("secret-token"));
        assertTrue(message.contains("\"password\":\"******\""));
        assertTrue(message.contains("\"accessToken\":\"******\""));
        assertTrue(message.matches(".* - 耗时：\\d+ms"));
    }

    @Test
    void shouldLogFormAndRepeatedParameters() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/form");
        request.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        request.addParameter("name", "demo");
        request.addParameter("tag", "one", "two");
        request.addParameter("password", "must-not-leak");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (wrappedRequest, wrappedResponse) -> {
            wrappedResponse.setContentType(MediaType.TEXT_PLAIN_VALUE);
            wrappedResponse.getWriter().write("ok");
        });

        String message = singleLogMessage();
        assertTrue(message.contains("\"name\":\"demo\""));
        assertTrue(message.contains("\"tag\":[\"one\",\"two\"]"));
        assertTrue(message.contains("\"password\":\"******\""));
        assertFalse(message.contains("must-not-leak"));
        assertTrue(message.contains("处理结果：ok"));
    }

    @Test
    void shouldMaskChatRequestAndResponseContent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ai/chat/start");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setContent("{\"applicationCode\":\"support\",\"query\":\"私人问题\"}"
                .getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (wrappedRequest, wrappedResponse) -> {
            wrappedRequest.getInputStream().readAllBytes();
            wrappedResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            wrappedResponse.getWriter().write("{\"code\":200,\"data\":{\"answer\":\"私人回答\"}}");
        });

        String message = singleLogMessage();
        assertTrue(message.contains("\"applicationCode\":\"support\""));
        assertTrue(message.contains("\"query\":\"******\""));
        assertTrue(message.contains("\"answer\":\"******\""));
        assertFalse(message.contains("私人问题"));
        assertFalse(message.contains("私人回答"));
    }

    @Test
    void shouldRemoveSignedUrlParametersAndTruncateLargeResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String signedUrl = "http://storage.local/avatar.png?X-Amz-Signature=secret-signature";
        String largeValue = "x".repeat(5 * 1024);
        LinkedHashMap<String, String> responseData = new LinkedHashMap<>();
        responseData.put("avatarUrl", signedUrl);
        responseData.put("description", largeValue);

        filter.doFilter(request, response, (wrappedRequest, wrappedResponse) -> {
            wrappedResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            wrappedResponse.getWriter().write(JSON.toJSONString(responseData));
        });

        String message = singleLogMessage();
        assertTrue(message.contains("http://storage.local/avatar.png"));
        assertFalse(message.contains("X-Amz-Signature"));
        assertFalse(message.contains("secret-signature"));
        assertTrue(message.contains("...(已截断)"));
    }

    @Test
    void shouldPassThroughSseImmediatelyAndLogAfterAsyncCompletion() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/chat/stream");
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AsyncContext[] asyncContext = new AsyncContext[1];

        filter.doFilter(request, response, (wrappedRequest, wrappedResponse) -> {
            wrappedResponse.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            asyncContext[0] = wrappedRequest.startAsync(wrappedRequest, wrappedResponse);
            wrappedResponse.getWriter().write("event: message\ndata: hello\n\n");
            wrappedResponse.getWriter().flush();
            assertEquals("event: message\ndata: hello\n\n", response.getContentAsString());
        });

        assertTrue(appender.list.isEmpty());
        asyncContext[0].complete();

        String message = singleLogMessage();
        assertTrue(message.contains("处理结果：<SSE 流式响应>"));
        assertFalse(message.contains("data: hello"));
    }

    private String singleLogMessage() {
        assertEquals(1, appender.list.size());
        return appender.list.getFirst().getFormattedMessage();
    }
}

package com.lingxi.workbench.framework.web.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.AsyncContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void shouldLogOnlyRequestMetadataWithoutBusinessPayload() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setParameter("source", "web");
        request.setContent("{\"username\":\"private-user\",\"password\":\"private-password\"}"
                .getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            servletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            servletResponse.getWriter().write("{\"code\":200,\"data\":{\"token\":\"private-token\"}}");
        });

        assertEquals("{\"code\":200,\"data\":{\"token\":\"private-token\"}}", response.getContentAsString());
        String message = singleLogMessage();
        assertTrue(message.contains("HTTP接口 method=POST uri=/api/v1/auth/login status=200 errorType=NONE"));
        assertFalse(message.contains("private-user"));
        assertFalse(message.contains("private-password"));
        assertFalse(message.contains("private-token"));
        assertFalse(message.contains("source=web"));
    }

    @Test
    void shouldLogFailureTypeWithoutExceptionMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tools/detail/7");
        MockHttpServletResponse response = new MockHttpServletResponse();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> filter.doFilter(request, response, (servletRequest, servletResponse) -> {
                    throw new IllegalStateException("private failure detail");
                }));

        assertEquals("private failure detail", exception.getMessage());
        String message = singleLogMessage();
        assertTrue(message.contains("HTTP接口 method=GET uri=/api/v1/tools/detail/7 status=200 errorType=IllegalStateException"));
        assertFalse(message.contains("private failure detail"));
    }

    @Test
    void shouldLogAsyncRequestOnlyAfterCompletionWithoutSseContent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/chat/stream");
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AsyncContext[] asyncContext = new AsyncContext[1];

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            servletResponse.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            asyncContext[0] = servletRequest.startAsync(servletRequest, servletResponse);
            servletResponse.getWriter().write("event: message\\ndata: private-event\\n\\n");
            servletResponse.getWriter().flush();
        });

        assertTrue(appender.list.isEmpty());
        asyncContext[0].complete();

        String message = singleLogMessage();
        assertTrue(message.contains("HTTP接口 method=GET uri=/api/v1/chat/stream status=200 errorType=NONE"));
        assertFalse(message.contains("private-event"));
    }

    private String singleLogMessage() {
        assertEquals(1, appender.list.size());
        return appender.list.getFirst().getFormattedMessage();
    }
}

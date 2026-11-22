package com.lingxi.workbench.framework.web.sse;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SseResponseHeaderFilterTest {

    private final SseResponseHeaderFilter filter = new SseResponseHeaderFilter();

    @Test
    void shouldDisableTransformationAndBufferingForSseRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ACCEPT,
                MediaType.TEXT_EVENT_STREAM_VALUE + ", " + MediaType.APPLICATION_JSON_VALUE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("no-cache, no-transform", response.getHeader(HttpHeaders.CACHE_CONTROL));
        assertEquals("identity", response.getHeader(HttpHeaders.CONTENT_ENCODING));
        assertEquals("no", response.getHeader("X-Accel-Buffering"));
    }

    @Test
    void shouldLeaveNormalJsonResponseUntouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNull(response.getHeader(HttpHeaders.CACHE_CONTROL));
        assertNull(response.getHeader(HttpHeaders.CONTENT_ENCODING));
        assertNull(response.getHeader("X-Accel-Buffering"));
    }

    @Test
    void shouldNotTreatWildcardAcceptAsSseRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNull(response.getHeader(HttpHeaders.CACHE_CONTROL));
        assertNull(response.getHeader(HttpHeaders.CONTENT_ENCODING));
        assertNull(response.getHeader("X-Accel-Buffering"));
    }
}

package com.lingxi.workbench.framework.web.sse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 统一设置 SSE 响应头，防止网关或反向代理压缩、转换和缓冲事件流。
 *
 * <p>过滤器按标准 {@code Accept: text/event-stream} 请求头识别流式请求，
 * 不依赖具体接口路径，业务模块无需重复处理传输层细节。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SseResponseHeaderFilter extends OncePerRequestFilter {

    private static final String NO_BUFFERING_HEADER = "X-Accel-Buffering";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept == null || accept.isBlank()) {
            return true;
        }
        try {
            return MediaType.parseMediaTypes(accept).stream().noneMatch(SseResponseHeaderFilter::isEventStream);
        } catch (InvalidMediaTypeException ignored) {
            return true;
        }
    }

    private static boolean isEventStream(MediaType mediaType) {
        return MediaType.TEXT_EVENT_STREAM.getType().equalsIgnoreCase(mediaType.getType()) && MediaType.TEXT_EVENT_STREAM.getSubtype().equalsIgnoreCase(mediaType.getSubtype());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        response.setHeader(HttpHeaders.CONTENT_ENCODING, "identity");
        response.setHeader(NO_BUFFERING_HEADER, "no");
        filterChain.doFilter(request, response);
    }
}

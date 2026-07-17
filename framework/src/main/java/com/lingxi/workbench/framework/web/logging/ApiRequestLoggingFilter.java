package com.lingxi.workbench.framework.web.logging;

import cn.hutool.core.text.CharSequenceUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 记录 HTTP 接口的请求参数、处理结果和耗时。
 *
 * <p>响应包装器采用透传采样而不是缓存整个响应，避免阻塞 SSE 等流式响应。
 * 日志内容有长度上限，并对凭证、Prompt 和工具参数等敏感字段脱敏。</p>
 */
@Slf4j
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    private static final int MAX_CACHED_PAYLOAD_BYTES = 64 * 1024;
    private static final int MAX_LOG_CONTENT_CHARS = 16 * 1024;
    private static final String MASKED_VALUE = "******";
    private static final Set<String> SENSITIVE_KEY_PARTS = Set.of(
            "password", "passwd", "pwd", "secret", "token", "authorization", "cookie",
            "apikey", "accesskey", "privatekey", "prompt", "toolargument", "toolparameter"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!log.isInfoEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        long startNanos = System.nanoTime();
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(
                request, MAX_CACHED_PAYLOAD_BYTES);
        SamplingHttpServletResponseWrapper responseWrapper = new SamplingHttpServletResponseWrapper(
                response, MAX_CACHED_PAYLOAD_BYTES);
        AtomicBoolean logged = new AtomicBoolean(false);
        AtomicReference<Throwable> asyncFailure = new AtomicReference<>();

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } catch (IOException | ServletException | RuntimeException exception) {
            asyncFailure.set(exception);
            throw exception;
        } finally {
            if (requestWrapper.isAsyncStarted()) {
                registerAsyncLogging(requestWrapper, responseWrapper, startNanos, logged, asyncFailure);
            } else {
                logOnce(requestWrapper, responseWrapper, startNanos, logged, asyncFailure.get());
            }
        }
    }

    private static void registerAsyncLogging(ContentCachingRequestWrapper request,
                                             SamplingHttpServletResponseWrapper response,
                                             long startNanos,
                                             AtomicBoolean logged,
                                             AtomicReference<Throwable> failure) {
        AsyncListener listener = new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) {
                logOnce(request, response, startNanos, logged, failure.get());
            }

            @Override
            public void onTimeout(AsyncEvent event) {
                failure.compareAndSet(null, new IllegalStateException("异步请求超时"));
            }

            @Override
            public void onError(AsyncEvent event) {
                failure.compareAndSet(null, event.getThrowable());
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
                event.getAsyncContext().addListener(this);
            }
        };

        try {
            request.getAsyncContext().addListener(listener);
        } catch (IllegalStateException exception) {
            // 异步任务可能恰好在注册监听器前完成，此时立即补记日志。
            logOnce(request, response, startNanos, logged, failure.get());
        }
    }

    private static void logOnce(ContentCachingRequestWrapper request,
                                SamplingHttpServletResponseWrapper response,
                                long startNanos,
                                AtomicBoolean logged,
                                Throwable failure) {
        if (!logged.compareAndSet(false, true)) {
            return;
        }

        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
        String result = failure == null
                ? readResponseResult(response)
                : "异常：" + failure.getClass().getSimpleName();
        log.info("请求接口地址 uri：{} - 请求参数：{} - 处理结果：{} - 耗时：{}ms",
                request.getRequestURI(), readRequestParameters(request), result, elapsedMillis);
    }

    private static String readRequestParameters(ContentCachingRequestWrapper request) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) ->
                parameters.put(key, isSensitiveKey(key) ? MASKED_VALUE : simplifyValues(values)));

        String body = decode(request.getContentAsByteArray(), request.getCharacterEncoding());
        Object sanitizedBody = sanitizeBody(body, request.getContentType());
        if (Objects.nonNull(sanitizedBody)) {
            if (parameters.isEmpty()) {
                return truncate(toLogString(sanitizedBody));
            }
            Map<String, Object> combined = new LinkedHashMap<>();
            combined.put("formOrParam", parameters);
            combined.put("body", sanitizedBody);
            return truncate(JSON.toJSONString(combined));
        }
        return parameters.isEmpty() ? "{}" : truncate(JSON.toJSONString(parameters));
    }

    private static Object simplifyValues(String[] values) {
        if (values == null || values.length == 0) {
            return "";
        }
        return values.length == 1 ? values[0] : List.of(values);
    }

    private static Object sanitizeBody(String body, String contentType) {
        if (CharSequenceUtil.isBlank(body)) {
            return null;
        }
        if (isFormContentType(contentType)) {
            // 表单字段已从 parameterMap 结构化读取，避免重复记录原始编码内容而绕过字段脱敏。
            return null;
        }
        if (isJsonContentType(contentType)) {
            try {
                return sanitizeJsonValue(JSON.parse(body));
            } catch (JSONException ignored) {
                return truncate(normalizePlainText(body));
            }
        }
        if (isTextContentType(contentType)) {
            return truncate(normalizePlainText(body));
        }
        return "<非文本请求体>";
    }

    private static String readResponseResult(SamplingHttpServletResponseWrapper response) {
        String contentType = response.getContentType();
        if (isSseContentType(contentType)) {
            return "<SSE 流式响应>";
        }

        byte[] content = response.getSampledContent();
        if (content.length == 0) {
            return "<HTTP " + response.getStatus() + ">";
        }
        if (!isJsonContentType(contentType) && !isTextContentType(contentType)) {
            return "<非文本响应：" + (contentType == null ? "unknown" : contentType) + ">";
        }

        String body = decode(content, response.getCharacterEncoding());
        if (isJsonContentType(contentType)) {
            try {
                return truncate(JSON.toJSONString(sanitizeJsonValue(JSON.parse(body))));
            } catch (JSONException ignored) {
                // 非法 JSON 按普通文本记录采样内容。
            }
        }
        return truncate(normalizePlainText(body));
    }

    @SuppressWarnings("unchecked")
    private static Object sanitizeJsonValue(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            source.forEach((key, nestedValue) -> {
                String stringKey = String.valueOf(key);
                sanitized.put(stringKey, isSensitiveKey(stringKey)
                        ? MASKED_VALUE
                        : sanitizeJsonValue(nestedValue));
            });
            return sanitized;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> sanitized = new ArrayList<>();
            iterable.forEach(item -> sanitized.add(sanitizeJsonValue(item)));
            return sanitized;
        }
        return value;
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SENSITIVE_KEY_PARTS.stream().anyMatch(normalized::contains);
    }

    private static boolean isJsonContentType(String contentType) {
        return Objects.nonNull(contentType) && (contentType.contains(MediaType.APPLICATION_JSON_VALUE)
                || contentType.contains("+json"));
    }

    private static boolean isTextContentType(String contentType) {
        return Objects.isNull(contentType)
                || contentType.startsWith("text/")
                || isFormContentType(contentType);
    }

    private static boolean isFormContentType(String contentType) {
        return Objects.nonNull(contentType) && (contentType.contains(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                || contentType.contains(MediaType.MULTIPART_FORM_DATA_VALUE));
    }

    private static boolean isSseContentType(String contentType) {
        return Objects.nonNull(contentType) && contentType.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    private static String decode(byte[] content, String encoding) {
        if (content.length == 0) {
            return "";
        }
        try {
            return new String(content, Charset.forName(encoding));
        } catch (Exception ignored) {
            return new String(content, StandardCharsets.UTF_8);
        }
    }

    private static String normalizePlainText(String value) {
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String toLogString(Object value) {
        return value instanceof String string ? string : JSON.toJSONString(value);
    }

    private static String truncate(String value) {
        if (value.length() <= MAX_LOG_CONTENT_CHARS) {
            return value;
        }
        return value.substring(0, MAX_LOG_CONTENT_CHARS) + "...(已截断)";
    }

    /**
     * 只采样有限响应内容，同时立即写入真实响应，不影响 flush 和流式传输。
     */
    private static final class SamplingHttpServletResponseWrapper extends HttpServletResponseWrapper {

        private final ByteArrayOutputStream sampledContent = new ByteArrayOutputStream();
        private final int contentLimit;
        private ServletOutputStream outputStream;
        private PrintWriter writer;

        private SamplingHttpServletResponseWrapper(HttpServletResponse response, int contentLimit) {
            super(response);
            this.contentLimit = contentLimit;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (writer != null) {
                throw new IllegalStateException("getWriter() 已被调用");
            }
            if (outputStream == null) {
                ServletOutputStream delegate = super.getOutputStream();
                outputStream = new ServletOutputStream() {
                    @Override
                    public boolean isReady() {
                        return delegate.isReady();
                    }

                    @Override
                    public void setWriteListener(WriteListener writeListener) {
                        delegate.setWriteListener(writeListener);
                    }

                    @Override
                    public void write(int value) throws IOException {
                        delegate.write(value);
                        sample(new byte[]{(byte) value}, 0, 1);
                    }

                    @Override
                    public void write(byte[] bytes, int offset, int length) throws IOException {
                        delegate.write(bytes, offset, length);
                        sample(bytes, offset, length);
                    }

                    @Override
                    public void flush() throws IOException {
                        delegate.flush();
                    }

                    @Override
                    public void close() throws IOException {
                        delegate.close();
                    }
                };
            }
            return outputStream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer != null) {
                return writer;
            }
            if (outputStream != null) {
                throw new IllegalStateException("getOutputStream() 已被调用");
            }
            writer = new PrintWriter(new OutputStreamWriter(getOutputStreamForWriter(), getCharacterEncoding()), true);
            return writer;
        }

        private ServletOutputStream getOutputStreamForWriter() throws IOException {
            ServletOutputStream stream = getOutputStream();
            outputStream = stream;
            return stream;
        }

        private synchronized void sample(byte[] bytes, int offset, int length) {
            int remaining = contentLimit - sampledContent.size();
            if (remaining > 0) {
                sampledContent.write(bytes, offset, Math.min(remaining, length));
            }
        }

        private synchronized byte[] getSampledContent() {
            if (writer != null) {
                writer.flush();
            }
            return sampledContent.toByteArray();
        }
    }
}

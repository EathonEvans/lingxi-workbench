package com.lingxi.workbench.framework.web.logging;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 记录 HTTP 接口的最小化审计元数据。
 *
 * <p>请求体和响应体常携带用户资料、会话信息、Prompt、Tool 参数或资源地址；即使按字段名脱敏，
 * 也无法保证新增字段不会泄露。因此本过滤器只记录方法、URI、状态、异常类型和耗时，绝不采样业务载荷。
 * 异步请求在完成后统一记录，避免把 SSE 内容写入日志。</p>
 */
@Slf4j
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    /**
     * 拦截 HTTP 请求，在同步完成或异步完成后记录最小化元数据。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!log.isInfoEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        long startNanos = System.nanoTime();
        AtomicBoolean logged = new AtomicBoolean(false);
        AtomicReference<Throwable> asyncFailure = new AtomicReference<>();
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            asyncFailure.set(exception);
            throw exception;
        } finally {
            if (request.isAsyncStarted()) {
                registerAsyncLogging(request, response, startNanos, logged, asyncFailure);
            } else {
                logOnce(request, response, startNanos, logged, asyncFailure.get());
            }
        }
    }

    /**
     * 注册异步监听器，在请求真正结束后记录结果。
     */
    private static void registerAsyncLogging(HttpServletRequest request,
                                             HttpServletResponse response,
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
            // 异步请求可能恰好在注册前结束，此时立即补记一次元数据。
            logOnce(request, response, startNanos, logged, failure.get());
        }
    }

    /**
     * 确保同一请求只记录一次，且日志中不包含请求或响应业务载荷。
     */
    private static void logOnce(HttpServletRequest request,
                                HttpServletResponse response,
                                long startNanos,
                                AtomicBoolean logged,
                                Throwable failure) {
        if (!logged.compareAndSet(false, true)) {
            return;
        }
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
        String errorType = failure == null ? "NONE" : failure.getClass().getSimpleName();
        log.info("HTTP接口 method={} uri={} status={} errorType={} elapsedMs={}",
                request.getMethod(), request.getRequestURI(), response.getStatus(), errorType, elapsedMillis);
    }
}

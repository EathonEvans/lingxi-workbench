package com.lingxi.workbench.framework.web;

import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class GlobalExceptionHandlerTest {

    @Test
    void shouldNotWriteAnotherResponseAfterAsyncConnectionBreaks() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        AsyncRequestNotUsableException exception = new AsyncRequestNotUsableException("broken pipe");

        assertDoesNotThrow(() -> handler.handleAsyncRequestNotUsableException(exception));
    }
}

package com.lingxi.workbench.framework.convention;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiResponseTest {

    @Test
    void shouldCreateSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success("data");

        assertEquals(200, response.code());
        assertEquals("data", response.data());
        assertEquals("成功", response.msg());
    }

    @Test
    void shouldCreateFailureResponse() {
        ApiResponse<Void> response = ApiResponse.failure(401, "用户名或密码错误");

        assertEquals(401, response.code());
        assertNull(response.data());
        assertEquals("用户名或密码错误", response.msg());
    }

    @Test
    void shouldRejectInvalidFailureResponse() {
        assertThrows(IllegalArgumentException.class, () -> ApiResponse.failure(200, "失败"));
        assertThrows(IllegalArgumentException.class, () -> ApiResponse.failure(500, " "));
    }
}

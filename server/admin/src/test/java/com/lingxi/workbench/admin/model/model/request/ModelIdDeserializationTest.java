package com.lingxi.workbench.admin.model.model.request;

import com.lingxi.workbench.framework.json.JSON;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelIdDeserializationTest {

    @Test
    void shouldParseStringIdWithoutPrecisionLoss() {
        CreateModelCredentialRequest request = JSON.parseObject(
                "{\"providerId\":\"2077574496283791361\",\"name\":\"主密钥\",\"apiKey\":\"sk-example-key\"}",
                CreateModelCredentialRequest.class);

        assertEquals(2077574496283791361L, request.providerId());
    }
}

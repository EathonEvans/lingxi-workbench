package com.lingxi.workbench.admin.model.service.impl;

import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelExtensionParameterValidatorTest {

    @Test
    void shouldAcceptNestedVendorParametersAndReturnDefensiveCopy() {
        JSONObject parameters = new JSONObject();
        parameters.put("thinking", JSONObject.of("type", "disabled"));

        JSONObject normalized = ModelExtensionParameterValidator.normalize(parameters);

        assertNotSame(parameters, normalized);
        assertEquals("disabled", normalized.getJSONObject("thinking").getString("type"));
    }

    @Test
    void shouldRejectPlatformManagedTopLevelField() {
        JSONObject parameters = JSONObject.of("model", "unauthorized-model");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ModelExtensionParameterValidator.normalize(parameters));

        assertEquals("扩展参数禁止覆盖平台字段：model", exception.getMessage());
    }

    @Test
    void shouldRejectSensitiveFieldAtAnyDepth() {
        JSONObject parameters = new JSONObject();
        parameters.put(
                "provider_options",
                Map.of("api_key", "should-not-be-stored"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ModelExtensionParameterValidator.normalize(parameters));

        assertEquals("扩展参数禁止保存敏感字段：api_key", exception.getMessage());
    }
}

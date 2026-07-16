package com.lingxi.workbench.model.model.converter;

import com.lingxi.workbench.model.model.entity.ModelRuntimeConfigEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelRuntimeConfigConverterTest {

    private final ModelRuntimeConfigConverter converter =
            Mappers.getMapper(ModelRuntimeConfigConverter.class);

    @Test
    void shouldConvertDatabaseProjectionToRuntimeConfig() {
        ModelRuntimeConfigEntity entity = new ModelRuntimeConfigEntity();
        entity.setInstanceId(2077574496283791361L);
        entity.setProtocol("OPENAI_COMPATIBLE");
        entity.setBaseUrl("https://api.deepseek.com");
        entity.setSecretCiphertext("ciphertext");
        entity.setSecretIv("iv");
        entity.setModelCode("deepseek-chat");
        entity.setTemperature(new BigDecimal("0.700"));
        entity.setTopP(new BigDecimal("0.900"));
        entity.setStopSequences("[\"END\"]");
        entity.setTimeoutSeconds(60);
        entity.setMaxRetries(1);

        var businessConfig = converter.toBO(entity);
        var runtimeConfig = converter.toRuntimeConfig(businessConfig, "secret");

        assertEquals(2077574496283791361L, runtimeConfig.instanceId());
        assertEquals("secret", runtimeConfig.apiKey());
        assertEquals("deepseek-chat", runtimeConfig.modelCode());
        assertEquals(0.7, runtimeConfig.temperature());
        assertEquals(0.9, runtimeConfig.topP());
        assertEquals(List.of("END"), runtimeConfig.stopSequences());
    }
}

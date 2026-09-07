package com.lingxi.workbench.model.service.impl;

import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.security.SecretCipher;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.model.model.bo.ModelRuntimeConfigBO;
import com.lingxi.workbench.model.model.converter.ModelRuntimeConfigConverter;
import com.lingxi.workbench.model.repo.ModelRuntimeConfigRepo;
import org.junit.jupiter.api.Test;

import org.mapstruct.factory.Mappers;
import com.lingxi.workbench.infra.ai.rerank.model.RerankModelRuntimeConfig;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ModelRuntimeConfigServiceImplTest {

    @Test
    void shouldDecryptAndBuildDefaultRuntimeConfig() {
        ModelRuntimeConfigRepo repo = mock(ModelRuntimeConfigRepo.class);
        ModelRuntimeConfigConverter converter = mock(ModelRuntimeConfigConverter.class);
        SecretCipher cipher = mock(SecretCipher.class);
        ModelRuntimeConfigBO config = config("OPENAI_COMPATIBLE");
        ChatModelRuntimeConfig expected = ChatModelRuntimeConfig.builder()
                .instanceId(1L)
                .baseUrl("https://api.deepseek.com")
                .apiKey("secret")
                .modelCode("deepseek-chat")
                .temperature(0.7)
                .maxTokens(1024)
                .stopSequences(List.of())
                .timeoutSeconds(60)
                .maxRetries(1)
                .build();
        when(repo.findDefaultChatModel()).thenReturn(Optional.of(config));
        when(cipher.decrypt("ciphertext", "iv")).thenReturn("secret");
        when(converter.toRuntimeConfig(config, "secret")).thenReturn(expected);
        ModelRuntimeConfigServiceImpl service =
                new ModelRuntimeConfigServiceImpl(repo, converter, cipher);

        assertEquals(expected, service.getDefaultChatModel());
        verify(cipher).decrypt("ciphertext", "iv");
    }

    @Test
    void shouldRejectMissingDefaultModel() {
        ModelRuntimeConfigRepo repo = mock(ModelRuntimeConfigRepo.class);
        ModelRuntimeConfigConverter converter = mock(ModelRuntimeConfigConverter.class);
        SecretCipher cipher = mock(SecretCipher.class);
        when(repo.findDefaultChatModel()).thenReturn(Optional.empty());
        ModelRuntimeConfigServiceImpl service =
                new ModelRuntimeConfigServiceImpl(repo, converter, cipher);

        assertThrows(BusinessException.class, service::getDefaultChatModel);
        verifyNoInteractions(converter, cipher);
    }

    @Test
    void shouldRejectUnsupportedProtocolBeforeDecryptingSecret() {
        ModelRuntimeConfigRepo repo = mock(ModelRuntimeConfigRepo.class);
        ModelRuntimeConfigConverter converter = mock(ModelRuntimeConfigConverter.class);
        SecretCipher cipher = mock(SecretCipher.class);
        when(repo.findDefaultChatModel()).thenReturn(Optional.of(config("OTHER")));
        ModelRuntimeConfigServiceImpl service =
                new ModelRuntimeConfigServiceImpl(repo, converter, cipher);

        assertThrows(BusinessException.class, service::getDefaultChatModel);
        verifyNoInteractions(converter, cipher);
    }

    @Test
    void shouldBuildRerankConfigThroughRealConverter() {
        ModelRuntimeConfigRepo repo = mock(ModelRuntimeConfigRepo.class);
        SecretCipher cipher = mock(SecretCipher.class);
        when(repo.findRerankModel(1L)).thenReturn(Optional.of(config("OPENAI_COMPATIBLE")));
        when(cipher.decrypt("ciphertext", "iv")).thenReturn("secret");
        ModelRuntimeConfigServiceImpl service = new ModelRuntimeConfigServiceImpl(repo,
                Mappers.getMapper(ModelRuntimeConfigConverter.class), cipher);
        RerankModelRuntimeConfig result = service.getRerankModelConfig(1L);
        assertEquals(1L, result.getInstanceId());
        assertEquals("secret", result.getApiKey());
        assertEquals(60, result.getTimeoutSeconds());
    }

    @Test
    void shouldRejectUnavailableRerankBeforeDecrypting() {
        ModelRuntimeConfigRepo repo = mock(ModelRuntimeConfigRepo.class);
        SecretCipher cipher = mock(SecretCipher.class);
        ModelRuntimeConfigConverter converter = mock(ModelRuntimeConfigConverter.class);
        ModelRuntimeConfigServiceImpl service = new ModelRuntimeConfigServiceImpl(repo, converter, cipher);
        assertThrows(BusinessException.class, () -> service.getRerankModelConfig(9L));
        verifyNoInteractions(converter, cipher);
    }

    private static ModelRuntimeConfigBO config(String protocol) {
        return ModelRuntimeConfigBO.builder()
                .instanceId(1L)
                .protocol(protocol)
                .baseUrl("https://api.deepseek.com")
                .secretCiphertext("ciphertext")
                .secretIv("iv")
                .modelCode("deepseek-chat")
                .temperature(new BigDecimal("0.7"))
                .maxTokens(1024)
                .stopSequences(List.of())
                .timeoutSeconds(60)
                .maxRetries(1)
                .build();
    }
}

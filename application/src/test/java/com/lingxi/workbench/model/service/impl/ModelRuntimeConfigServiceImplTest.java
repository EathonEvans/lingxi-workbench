package com.lingxi.workbench.model.service.impl;

import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.security.SecretCipher;
import com.lingxi.workbench.infra.ai.chat.ChatModelRuntimeConfig;
import com.lingxi.workbench.model.model.bo.ModelRuntimeConfigBO;
import com.lingxi.workbench.model.model.converter.ModelRuntimeConfigConverter;
import com.lingxi.workbench.model.repo.ModelRuntimeConfigRepo;
import org.junit.jupiter.api.Test;

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
        ChatModelRuntimeConfig expected = new ChatModelRuntimeConfig(
                1L, "https://api.deepseek.com", "secret", "deepseek-chat",
                0.7, null, 1024, List.of(), 60, 1);
        when(repo.findDefaultChatModel()).thenReturn(Optional.of(config));
        when(cipher.decrypt("ciphertext", "iv")).thenReturn("secret");
        when(converter.toRuntimeConfig(config, "secret")).thenReturn(expected);
        var service = new ModelRuntimeConfigServiceImpl(repo, converter, cipher);

        assertEquals(expected, service.getDefaultChatModel());
        verify(cipher).decrypt("ciphertext", "iv");
    }

    @Test
    void shouldRejectMissingDefaultModel() {
        ModelRuntimeConfigRepo repo = mock(ModelRuntimeConfigRepo.class);
        ModelRuntimeConfigConverter converter = mock(ModelRuntimeConfigConverter.class);
        SecretCipher cipher = mock(SecretCipher.class);
        when(repo.findDefaultChatModel()).thenReturn(Optional.empty());
        var service = new ModelRuntimeConfigServiceImpl(repo, converter, cipher);

        assertThrows(BusinessException.class, service::getDefaultChatModel);
        verifyNoInteractions(converter, cipher);
    }

    @Test
    void shouldRejectUnsupportedProtocolBeforeDecryptingSecret() {
        ModelRuntimeConfigRepo repo = mock(ModelRuntimeConfigRepo.class);
        ModelRuntimeConfigConverter converter = mock(ModelRuntimeConfigConverter.class);
        SecretCipher cipher = mock(SecretCipher.class);
        when(repo.findDefaultChatModel()).thenReturn(Optional.of(config("OTHER")));
        var service = new ModelRuntimeConfigServiceImpl(repo, converter, cipher);

        assertThrows(BusinessException.class, service::getDefaultChatModel);
        verifyNoInteractions(converter, cipher);
    }

    private static ModelRuntimeConfigBO config(String protocol) {
        return new ModelRuntimeConfigBO(
                1L,
                protocol,
                "https://api.deepseek.com",
                "ciphertext",
                "iv",
                "deepseek-chat",
                new BigDecimal("0.7"),
                null,
                1024,
                List.of(),
                60,
                1);
    }
}

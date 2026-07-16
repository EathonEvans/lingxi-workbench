package com.lingxi.workbench.admin.model.service.impl;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelConnectionTestStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelCredentialStatus;
import com.lingxi.workbench.admin.model.model.bo.ModelConnectionTestResult;
import com.lingxi.workbench.admin.model.model.converter.ModelConverter;
import com.lingxi.workbench.admin.model.model.entity.ModelCredentialEntity;
import com.lingxi.workbench.admin.model.model.entity.ModelInstanceEntity;
import com.lingxi.workbench.admin.model.model.entity.ModelProviderEntity;
import com.lingxi.workbench.admin.model.repo.ModelCredentialRepo;
import com.lingxi.workbench.admin.model.repo.ModelInstanceRepo;
import com.lingxi.workbench.admin.model.repo.ModelProviderRepo;
import com.lingxi.workbench.admin.model.service.ModelConnectionTester;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.security.SecretCipher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelInstanceServiceImplTest {

    @Mock private ModelProviderRepo providerRepo;
    @Mock private ModelCredentialRepo credentialRepo;
    @Mock private ModelInstanceRepo instanceRepo;
    @Mock private AuditService auditService;
    @Mock private SecretCipher cipher;
    @Mock private ModelConnectionTester connectionTester;
    @Mock private ModelConverter modelConverter;

    @InjectMocks private ModelInstanceServiceImpl service;

    @Test
    void shouldPersistConnectionTestResult() {
        ModelProviderEntity provider = new ModelProviderEntity();
        provider.setId(11L);
        provider.setProtocol("OPENAI_COMPATIBLE");
        provider.setBaseUrl("https://example.com/v1");
        provider.setStatus(EnableStatus.ACTIVE);

        ModelCredentialEntity credential = new ModelCredentialEntity();
        credential.setId(12L);
        credential.setStatus(ModelCredentialStatus.ACTIVE);
        credential.setSecretCiphertext("ciphertext");
        credential.setSecretIv("iv");

        ModelInstanceEntity instance = new ModelInstanceEntity();
        instance.setId(13L);
        instance.setProviderId(11L);
        instance.setCredentialId(12L);
        instance.setModelCode("gpt-test");
        instance.setTimeoutSeconds(30);

        when(instanceRepo.findById(13L)).thenReturn(Optional.of(instance));
        when(providerRepo.findById(11L)).thenReturn(Optional.of(provider));
        when(credentialRepo.findById(12L)).thenReturn(Optional.of(credential));
        when(cipher.decrypt("ciphertext", "iv")).thenReturn("secret-key");
        when(connectionTester.test(
                        "OPENAI_COMPATIBLE",
                        "https://example.com/v1",
                        "secret-key",
                        "gpt-test",
                        30))
                .thenReturn(ModelConnectionTestResult.success(86));

        service.testConnection(13L, 1L);

        assertEquals(ModelConnectionTestStatus.SUCCESS, instance.getLastTestStatus());
        assertEquals(86, instance.getLastTestLatencyMs());
        assertEquals("连接成功", instance.getLastTestMessage());
        assertNotNull(instance.getLastTestAt());
        verify(instanceRepo).save(instance);
        verify(auditService).record(
                "MODEL", "INSTANCE", 13L, 1L, "MODEL_CONNECTION_TEST", "测试状态=SUCCESS, 耗时=86ms");
    }
}

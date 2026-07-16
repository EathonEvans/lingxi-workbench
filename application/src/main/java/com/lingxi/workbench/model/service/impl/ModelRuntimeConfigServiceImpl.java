package com.lingxi.workbench.model.service.impl;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.security.SecretCipher;
import com.lingxi.workbench.infra.ai.chat.ChatModelRuntimeConfig;
import com.lingxi.workbench.model.model.bo.ModelRuntimeConfigBO;
import com.lingxi.workbench.model.model.converter.ModelRuntimeConfigConverter;
import com.lingxi.workbench.model.repo.ModelRuntimeConfigRepo;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 模型运行配置服务实现。 */
@Service
@RequiredArgsConstructor
public class ModelRuntimeConfigServiceImpl implements ModelRuntimeConfigService {

    private static final String OPENAI_COMPATIBLE = "OPENAI_COMPATIBLE";

    private final ModelRuntimeConfigRepo configRepo;
    private final ModelRuntimeConfigConverter converter;
    private final SecretCipher secretCipher;

    /**
     * 查询并解密默认对话模型配置。
     *
     * <p>该方法只执行短事务数据库查询，解密后的 API Key 不进入任何持久化介质。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public ChatModelRuntimeConfig getDefaultChatModel() {
        ModelRuntimeConfigBO config = configRepo.findDefaultChatModel()
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "当前没有可用的默认对话模型"));
        if (!OPENAI_COMPATIBLE.equals(config.protocol())) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "默认模型协议暂不支持");
        }

        String apiKey = secretCipher.decrypt(config.secretCiphertext(), config.secretIv());
        return converter.toRuntimeConfig(config, apiKey);
    }
}

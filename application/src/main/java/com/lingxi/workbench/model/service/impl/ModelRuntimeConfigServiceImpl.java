package com.lingxi.workbench.model.service.impl;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.security.SecretCipher;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
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
        // Repo 联合查询模型实例、供应商和密钥，只返回三者均启用且被标记为默认 CHAT 模型的配置。
        ModelRuntimeConfigBO config = configRepo.findDefaultChatModel()
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "当前没有可用的默认对话模型"));
        // application 当前只接入 OpenAI 兼容协议，防止未知协议误入现有网关。
        if (!OPENAI_COMPATIBLE.equals(config.protocol())) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "默认模型协议暂不支持");
        }

        // 密钥只在真正发起模型调用前解密，明文仅存在于当前 JVM 内存，不回写数据库或进入日志。
        String apiKey = secretCipher.decrypt(config.secretCiphertext(), config.secretIv());

        // MapStruct 将数据库业务对象与明文密钥组装成 infra-ai 所需的最小运行参数。
        return converter.toRuntimeConfig(config, apiKey);
    }
}

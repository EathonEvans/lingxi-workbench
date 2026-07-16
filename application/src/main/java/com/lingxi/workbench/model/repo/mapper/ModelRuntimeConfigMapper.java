package com.lingxi.workbench.model.repo.mapper;

import com.lingxi.workbench.model.model.entity.ModelRuntimeConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 模型运行配置只读数据访问接口。 */
@Mapper
public interface ModelRuntimeConfigMapper {

    /** 联合查询当前启用的默认对话模型、供应商和访问密钥。 */
    @Select("""
            SELECT instance.id                AS instance_id,
                   provider.protocol          AS protocol,
                   provider.base_url          AS base_url,
                   credential.secret_ciphertext AS secret_ciphertext,
                   credential.secret_iv       AS secret_iv,
                   instance.model_code        AS model_code,
                   instance.temperature       AS temperature,
                   instance.top_p             AS top_p,
                   instance.max_tokens        AS max_tokens,
                   instance.stop_sequences::text AS stop_sequences,
                   instance.timeout_seconds   AS timeout_seconds,
                   instance.max_retries       AS max_retries
            FROM lingxi_core.ai_model_instance instance
            JOIN lingxi_core.ai_model_provider provider
              ON provider.id = instance.provider_id
            JOIN lingxi_core.ai_model_credential credential
              ON credential.id = instance.credential_id
             AND credential.provider_id = provider.id
            WHERE instance.model_type = 'CHAT'
              AND instance.is_default = TRUE
              AND instance.status = 'ACTIVE'
              AND provider.status = 'ACTIVE'
              AND credential.status = 'ACTIVE'
              AND credential.secret_ciphertext IS NOT NULL
              AND credential.secret_iv IS NOT NULL
            LIMIT 1
            """)
    ModelRuntimeConfigEntity selectDefaultChatModel();
}

package com.lingxi.workbench.model.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.model.model.entity.ModelRuntimeConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

/** 模型运行配置只读数据访问接口。 */
@Mapper
public interface ModelRuntimeConfigMapper extends BaseMapper<ModelRuntimeConfigEntity> {

    /**
     * 联合查询当前启用的默认对话模型、供应商和访问密钥。
     *
     * <p>该 SQL 同时校验模型、供应商和密钥状态，并排除密文不完整的密钥记录。
     * 查询结果仍是加密密钥，解密操作由 Service 在事务查询完成后执行。</p>
     */
    @Select("""
            SELECT instance.id                AS id,
                   provider.protocol          AS protocol,
                   provider.base_url          AS base_url,
                   credential.secret_ciphertext AS secret_ciphertext,
                   credential.secret_iv       AS secret_iv,
                   instance.model_code        AS model_code,
                   instance.temperature       AS temperature,
                   instance.top_p             AS top_p,
                   instance.max_tokens        AS max_tokens,
                   instance.stop_sequences       AS stop_sequences,
                   instance.extension_parameters AS extension_parameters,
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
    @ResultMap("mybatis-plus_ModelRuntimeConfigEntity")
    ModelRuntimeConfigEntity selectDefaultChatModel();

    /** 按 AgentVersion 锁定的实例 ID 查询可用对话模型。 */
    @Select("""
            SELECT instance.id                  AS id,
                   provider.protocol            AS protocol,
                   provider.base_url            AS base_url,
                   credential.secret_ciphertext AS secret_ciphertext,
                   credential.secret_iv         AS secret_iv,
                   instance.model_code           AS model_code,
                   instance.temperature          AS temperature,
                   instance.top_p                AS top_p,
                   instance.max_tokens           AS max_tokens,
                   instance.stop_sequences       AS stop_sequences,
                   instance.extension_parameters AS extension_parameters,
                   instance.timeout_seconds      AS timeout_seconds,
                   instance.max_retries          AS max_retries
            FROM lingxi_core.ai_model_instance instance
            JOIN lingxi_core.ai_model_provider provider
              ON provider.id = instance.provider_id
            JOIN lingxi_core.ai_model_credential credential
              ON credential.id = instance.credential_id
             AND credential.provider_id = provider.id
            WHERE instance.id = #{instanceId}
              AND instance.model_type = 'CHAT'
              AND instance.status = 'ACTIVE'
              AND provider.status = 'ACTIVE'
              AND credential.status = 'ACTIVE'
              AND credential.secret_ciphertext IS NOT NULL
              AND credential.secret_iv IS NOT NULL
            """)
    @ResultMap("mybatis-plus_ModelRuntimeConfigEntity")
    ModelRuntimeConfigEntity selectChatModel(@Param("instanceId") Long instanceId);

    /**
     * 查询库中最新创建的可用 Embedding 模型配置。
     */
    @Select("""
            SELECT instance.id                  AS id,
                   provider.protocol           AS protocol,
                   provider.base_url           AS base_url,
                   credential.secret_ciphertext AS secret_ciphertext,
                   credential.secret_iv        AS secret_iv,
                   instance.model_code         AS model_code,
                   instance.temperature        AS temperature,
                   instance.top_p              AS top_p,
                   instance.max_tokens         AS max_tokens,
                   instance.stop_sequences     AS stop_sequences,
                   instance.extension_parameters AS extension_parameters,
                   instance.timeout_seconds    AS timeout_seconds,
                   instance.max_retries        AS max_retries
            FROM lingxi_core.ai_model_instance instance
            JOIN lingxi_core.ai_model_provider provider
              ON provider.id = instance.provider_id
            JOIN lingxi_core.ai_model_credential credential
              ON credential.id = instance.credential_id
             AND credential.provider_id = provider.id
            WHERE instance.model_type = 'EMBEDDING'
              AND instance.status = 'ACTIVE'
              AND provider.status = 'ACTIVE'
              AND credential.status = 'ACTIVE'
              AND credential.secret_ciphertext IS NOT NULL
              AND credential.secret_iv IS NOT NULL
            and instance.id = #{instanceId}
            """)
    @ResultMap("mybatis-plus_ModelRuntimeConfigEntity")
    ModelRuntimeConfigEntity selectEmbeddingModel(@Param("instanceId") Long instanceId);
}

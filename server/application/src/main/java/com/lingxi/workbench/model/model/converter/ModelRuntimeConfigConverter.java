package com.lingxi.workbench.model.model.converter;

import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.model.model.bo.ModelRuntimeConfigBO;
import com.lingxi.workbench.model.model.entity.ModelRuntimeConfigEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import com.lingxi.workbench.infra.ai.rerank.model.RerankModelRuntimeConfig;
import java.util.List;

/**
 * 模型运行配置查询结果、业务对象和 infra-ai 调用参数转换器。
 *
 * <p>负责 Entity → BO 及 BO → 运行时配置的多步转换，并自动解密密钥。</p>
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ModelRuntimeConfigConverter {

    /**
     * 将数据库运行配置转换为业务对象，停止序列字段自动处理 null。
     *
     * @param entity 模型运行配置数据库实体
     * @return 业务对象
     */
    @Mapping(target = "stopSequences", qualifiedByName = "emptyIfNull")
    ModelRuntimeConfigBO toBO(ModelRuntimeConfigEntity entity);

    /**
     * 将已校验配置和解密后的密钥组装为 Chat 模型调用参数。
     *
     * @param config 模型运行配置业务对象
     * @param apiKey 解密后的 API 密钥
     * @return Chat 模型运行时配置
     */
    @Mapping(target = "instanceId", source = "config.instanceId")
    @Mapping(target = "baseUrl", source = "config.baseUrl")
    @Mapping(target = "apiKey", source = "apiKey")
    @Mapping(target = "modelCode", source = "config.modelCode")
    @Mapping(target = "temperature", source = "config.temperature")
    @Mapping(target = "topP", source = "config.topP")
    @Mapping(target = "maxTokens", source = "config.maxTokens")
    @Mapping(target = "stopSequences", source = "config.stopSequences")
    @Mapping(target = "extensionParameters", source = "config.extensionParameters",
            qualifiedByName = "emptyJsonIfNull")
    @Mapping(target = "timeoutSeconds", source = "config.timeoutSeconds")
    @Mapping(target = "maxRetries", source = "config.maxRetries")
    @Mapping(target = "structuredOutputSupported", source = "config.structuredOutputSupported")
    ChatModelRuntimeConfig toRuntimeConfig(ModelRuntimeConfigBO config, String apiKey);

    /**
     * 将 null 列表替换为空列表，保证下游消费者不会因 null 集合而 NPE。
     *
     * @param values 可能为 null 的字符串列表
     * @return 非 null 列表
     */
    @Named("emptyIfNull")
    default List<String> emptyIfNull(List<String> values) {
        return values == null ? List.of() : values;
    }

    /**
     * 复制扩展参数，隔离数据库实体、业务对象和模型调用配置之间的可变 JSON 引用。
     */
    @Named("emptyJsonIfNull")
    default JSONObject emptyJsonIfNull(JSONObject value) {
        if (value == null) {
            return new JSONObject();
        }
        return JSON.parseObject(JSON.toJSONString(value));
    }

    /**
     * 将已校验配置和解密后的密钥组装为 Embedding 模型调用参数。
     *
     * <p>与 Chat 模型不同，Embedding 调用不需要 temperature、topP 等生成参数。</p>
     *
     * @param config 模型运行配置业务对象
     * @param apiKey 解密后的 API 密钥
     * @return Embedding 模型运行时配置
     */
    @Mapping(target = "instanceId", source = "config.instanceId")
    @Mapping(target = "baseUrl", source = "config.baseUrl")
    @Mapping(target = "apiKey", source = "apiKey")
    @Mapping(target = "modelCode", source = "config.modelCode")
    @Mapping(target = "timeoutSeconds", source = "config.timeoutSeconds")
    @Mapping(target = "maxRetries", source = "config.maxRetries")
    EmbeddingModelRuntimeConfig toEmbeddingConfig(ModelRuntimeConfigBO config, String apiKey);

    /** 将已校验模型和解密凭证组装为重排序配置。 */
    @Mapping(target = "apiKey", source = "apiKey")
    RerankModelRuntimeConfig toRerankConfig(ModelRuntimeConfigBO config, String apiKey);
}

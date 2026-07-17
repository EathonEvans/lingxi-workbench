package com.lingxi.workbench.model.model.converter;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.model.model.bo.ModelRuntimeConfigBO;
import com.lingxi.workbench.model.model.entity.ModelRuntimeConfigEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.List;

/** 模型运行配置查询结果、业务对象和 infra-ai 调用参数转换器。 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ModelRuntimeConfigConverter {

    /** 将数据库运行配置转换为业务对象。 */
    @Mapping(target = "stopSequences", qualifiedByName = "emptyIfNull")
    ModelRuntimeConfigBO toBO(ModelRuntimeConfigEntity entity);

    /** 将已校验配置和解密后的密钥组装为单次模型调用参数。 */
    @Mapping(target = "instanceId", source = "config.instanceId")
    @Mapping(target = "baseUrl", source = "config.baseUrl")
    @Mapping(target = "apiKey", source = "apiKey")
    @Mapping(target = "modelCode", source = "config.modelCode")
    @Mapping(target = "temperature", source = "config.temperature")
    @Mapping(target = "topP", source = "config.topP")
    @Mapping(target = "maxTokens", source = "config.maxTokens")
    @Mapping(target = "stopSequences", source = "config.stopSequences")
    @Mapping(target = "timeoutSeconds", source = "config.timeoutSeconds")
    @Mapping(target = "maxRetries", source = "config.maxRetries")
    ChatModelRuntimeConfig toRuntimeConfig(ModelRuntimeConfigBO config, String apiKey);

    /** 保持运行时停止序列集合非空。 */
    @Named("emptyIfNull")
    default List<String> emptyIfNull(List<String> values) {
        return values == null ? List.of() : values;
    }

}

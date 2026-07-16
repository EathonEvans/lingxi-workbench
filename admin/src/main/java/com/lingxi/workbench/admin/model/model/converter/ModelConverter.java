package com.lingxi.workbench.admin.model.model.converter;

import com.alibaba.fastjson2.JSON;
import com.lingxi.workbench.admin.model.model.entity.ModelCredentialEntity;
import com.lingxi.workbench.admin.model.model.entity.ModelInstanceEntity;
import com.lingxi.workbench.admin.model.model.entity.ModelProviderEntity;
import com.lingxi.workbench.admin.model.model.vo.ModelCredentialVO;
import com.lingxi.workbench.admin.model.model.vo.ModelInstanceVO;
import com.lingxi.workbench.admin.model.model.vo.ModelProviderVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.List;

/** 模型供应商、访问密钥和模型实例的统一模型转换器。 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ModelConverter {

    /** 将供应商实体转换为响应对象，主键由 MapStruct 转换为字符串。 */
    ModelProviderVO toProviderVO(ModelProviderEntity entity);

    /** 将访问密钥实体转换为脱敏响应对象，主键由 MapStruct 转换为字符串。 */
    ModelCredentialVO toCredentialVO(ModelCredentialEntity entity);

    /** 将模型实例实体转换为响应对象，并解析 JSONB 数组字段。 */
    @Mapping(target = "capabilities", qualifiedByName = "parseJsonArray")
    @Mapping(target = "stopSequences", qualifiedByName = "parseJsonArray")
    ModelInstanceVO toInstanceVO(ModelInstanceEntity entity);

    /** 将数据库中的 JSON 数组转换为接口列表。 */
    @Named("parseJsonArray")
    default List<String> parseJsonArray(String value) {
        return value == null ? List.of() : JSON.parseArray(value, String.class);
    }
}

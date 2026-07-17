package com.lingxi.workbench.admin.model.model.converter;

import cn.hutool.core.collection.CollUtil;
import com.lingxi.workbench.admin.model.common.enums.ModelCapability;
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

    /** 将模型实例实体转换为响应对象。 */
    @Mapping(target = "stopSequences", qualifiedByName = "emptyIfNull")
    ModelInstanceVO toInstanceVO(ModelInstanceEntity entity);

    /** 将模型能力枚举转换为保持现有接口格式的字符串列表。 */
    default List<String> toCapabilityNames(List<ModelCapability> capabilities) {
        return CollUtil.isEmpty(capabilities)
                ? List.of()
                : capabilities.stream().map(Enum::name).toList();
    }

    /** 保持接口集合字段非空，兼容历史接口行为。 */
    @Named("emptyIfNull")
    default List<String> emptyIfNull(List<String> values) {
        return CollUtil.isEmpty(values) ? List.of() : values;
    }
}

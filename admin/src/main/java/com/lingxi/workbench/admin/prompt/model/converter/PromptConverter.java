package com.lingxi.workbench.admin.prompt.model.converter;

import com.alibaba.fastjson2.JSON;
import com.lingxi.workbench.admin.prompt.model.entity.PromptVersionEntity;
import com.lingxi.workbench.admin.prompt.model.entity.PromptEntity;
import com.lingxi.workbench.admin.prompt.model.vo.PromptDetailVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptVariableVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptVersionVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.List;

/** Prompt 实体与接口模型转换器。 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PromptConverter {

    /** 将 Prompt 主记录和版本摘要转换为列表响应。 */
    @Mapping(source = "entity.id", target = "id")
    @Mapping(source = "entity.publishedVersionId", target = "publishedVersionId")
    PromptVO toPromptVO(
            PromptEntity entity, Integer publishedVersionNo, Integer draftVersionNo);

    /** 将 Prompt 主记录和当前编辑版本转换为详情响应。 */
    @Mapping(source = "entity.id", target = "id")
    @Mapping(source = "entity.publishedVersionId", target = "publishedVersionId")
    PromptDetailVO toDetailVO(
            PromptEntity entity,
            Integer publishedVersionNo,
            String draftVersionId,
            Integer draftVersionNo,
            String promptContent,
            List<PromptVariableVO> variables,
            String changeNote);

    /** 将版本实体转换为响应模型，并解析 JSONB 变量声明。 */
    @Mapping(target = "variables", qualifiedByName = "parseVariables")
    PromptVersionVO toVersionVO(PromptVersionEntity entity);

    /** 将 JSONB 变量声明转换为接口列表。 */
    @Named("parseVariables")
    default List<PromptVariableVO> parseVariables(String value) {
        return value == null ? List.of() : JSON.parseArray(value, PromptVariableVO.class);
    }
}

package com.lingxi.workbench.admin.prompt.model.converter;

import cn.hutool.core.collection.CollUtil;
import com.lingxi.workbench.admin.prompt.model.entity.PromptVariableDefinition;
import com.lingxi.workbench.admin.prompt.model.entity.PromptVersionEntity;
import com.lingxi.workbench.admin.prompt.model.entity.PromptEntity;
import com.lingxi.workbench.admin.prompt.model.request.PromptVariableRequest;
import com.lingxi.workbench.admin.prompt.model.vo.PromptDetailVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptVariableVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptVersionVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

/** Prompt实体与接口模型转换器。 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PromptConverter {

    /** 将Prompt主记录和版本摘要转换为列表响应。 */
    @Mapping(source = "entity.id", target = "id")
    @Mapping(source = "entity.publishedVersionId", target = "publishedVersionId")
    PromptVO toPromptVO(
            PromptEntity entity, Integer publishedVersionNo, Integer draftVersionNo);

    /** 将Prompt主记录和当前编辑版本转换为详情响应。 */
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

    /** 将版本实体转换为响应模型。 */
    PromptVersionVO toVersionVO(PromptVersionEntity entity);

    /** 将接口请求中的变量声明转换为JSONB内嵌对象。 */
    PromptVariableDefinition toDefinition(PromptVariableRequest request);

    /** 批量转换接口请求中的变量声明。 */
    default List<PromptVariableDefinition> toDefinitions(List<PromptVariableRequest> requests) {
        return CollUtil.isEmpty(requests) ? List.of() : requests.stream().map(this::toDefinition).toList();
    }

    /** 将JSONB内嵌变量声明转换为接口响应。 */
    PromptVariableVO toVariableVO(PromptVariableDefinition definition);

    /** 批量转换JSONB内嵌变量声明。 */
    default List<PromptVariableVO> toVariableVOs(List<PromptVariableDefinition> definitions) {
        return CollUtil.isEmpty(definitions) ? List.of() : definitions.stream().map(this::toVariableVO).toList();
    }

    /** 将JSONB内嵌变量声明还原为模板校验参数。 */
    PromptVariableRequest toVariableRequest(PromptVariableDefinition definition);

    /** 批量还原模板校验参数。 */
    default List<PromptVariableRequest> toVariableRequests(
            List<PromptVariableDefinition> definitions) {
        return CollUtil.isEmpty(definitions)
                ? List.of()
                : definitions.stream().map(this::toVariableRequest).toList();
    }
}

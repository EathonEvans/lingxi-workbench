package com.lingxi.workbench.admin.tool.model.converter;

import com.lingxi.workbench.admin.tool.model.entity.ToolEntity;
import com.lingxi.workbench.admin.tool.model.vo.ToolOptionVO;
import com.lingxi.workbench.admin.tool.model.vo.ToolVO;
import com.lingxi.workbench.admin.tool.service.impl.ToolParameterSchemaFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Tool 持久化模型和接口模型转换器。 */
@Component
@RequiredArgsConstructor
public class ToolConverter {

    private final ToolParameterSchemaFactory parameterSchemaFactory;

    /** 转换 Tool 管理详情，并将后端 Schema 还原为结构化参数列表。 */
    public ToolVO toVO(ToolEntity entity) {
        return ToolVO.builder()
                .id(String.valueOf(entity.getId()))
                .name(entity.getName())
                .code(entity.getCode())
                .description(entity.getDescription())
                .sourceType(entity.getSourceType())
                .parameters(parameterSchemaFactory.parse(entity.getParameterSchema()))
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** 转换 Agent 可绑定的启用 Tool 选项。 */
    public ToolOptionVO toOptionVO(ToolEntity entity) {
        return ToolOptionVO.builder()
                .code(entity.getCode())
                .name(entity.getName())
                .description(entity.getDescription())
                .build();
    }

}

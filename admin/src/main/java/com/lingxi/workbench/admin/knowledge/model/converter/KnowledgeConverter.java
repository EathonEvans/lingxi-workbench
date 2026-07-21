package com.lingxi.workbench.admin.knowledge.model.converter;

import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeBaseEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeDocumentEntity;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeBaseVO;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeDocumentVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/** 知识库实体与接口模型转换器。 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface KnowledgeConverter {
    KnowledgeBaseVO toBaseVO(KnowledgeBaseEntity entity);
    KnowledgeDocumentVO toDocumentVO(KnowledgeDocumentEntity entity);
}

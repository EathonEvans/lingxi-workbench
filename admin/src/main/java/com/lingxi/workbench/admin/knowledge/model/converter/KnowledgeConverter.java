package com.lingxi.workbench.admin.knowledge.model.converter;

import com.lingxi.workbench.admin.knowledge.model.bo.KnowledgeChunkBO;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeBaseEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeDocumentEntity;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeBaseVO;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeChunkVO;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeDocumentVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/** 知识库实体与接口模型转换器。 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface KnowledgeConverter {
    /** 知识库实体转视图对象。 */
    KnowledgeBaseVO toBaseVO(KnowledgeBaseEntity entity);
    /** 知识库文档实体转视图对象。 */
    KnowledgeDocumentVO toDocumentVO(KnowledgeDocumentEntity entity);
    /** 文档分块业务对象列表转视图对象列表。 */
    List<KnowledgeChunkVO> toChunkVOList(List<KnowledgeChunkBO> chunks);
}

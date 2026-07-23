package com.lingxi.workbench.admin.knowledge.model.converter;

import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeBaseEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeDocumentEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeChunkEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeChunkTaskEntity;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeBaseVO;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeChunkVO;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeDocumentVO;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeChunkResultVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/** 知识库实体与接口模型转换器。 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface KnowledgeConverter {
    /** 知识库实体转视图对象。 */
    KnowledgeBaseVO toBaseVO(KnowledgeBaseEntity entity);
    /** 知识库文档实体转视图对象。 */
    KnowledgeDocumentVO toDocumentVO(KnowledgeDocumentEntity entity);
    /** 已持久化Chunk实体转视图对象。 */
    KnowledgeChunkVO toChunkVO(KnowledgeChunkEntity entity);
    /** 分块任务实体转状态视图。 */
    @Mapping(target = "taskId", source = "task.id")
    @Mapping(target = "documentId", source = "task.documentId")
    KnowledgeChunkResultVO toChunkTaskVO(KnowledgeChunkTaskEntity task, String documentName);
}

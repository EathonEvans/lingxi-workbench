package com.lingxi.workbench.admin.knowledge.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeChunkEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 文档Chunk数据访问层。 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunkEntity> {

    /** 单条SQL批量写入Chunk，避免逐条往返数据库。 */
    @Insert("""
            <script>
            INSERT INTO lingxi_core.ai_knowledge_chunk
                (task_id, knowledge_base_id, document_id, chunk_no, content, content_hash,
                 char_count, token_count, metadata, enabled, created_by, updated_by,
                 created_at, updated_at)
            VALUES
            <foreach collection="items" item="item" separator=",">
                (#{item.taskId}, #{item.knowledgeBaseId}, #{item.documentId}, #{item.chunkNo},
                 #{item.content}, #{item.contentHash}, #{item.charCount}, #{item.tokenCount},
                 #{item.metadata,typeHandler=com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler,jdbcType=OTHER},
                 #{item.enabled},
                 #{item.createdBy}, #{item.updatedBy},
                 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            </foreach>
            </script>
            """)
    int insertBatch(@Param("items") List<KnowledgeChunkEntity> items);
}

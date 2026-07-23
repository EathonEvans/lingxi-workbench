package com.lingxi.workbench.admin.knowledge.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeChunkTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

/** 文档分块任务数据访问层。 */
@Mapper
public interface KnowledgeChunkTaskMapper extends BaseMapper<KnowledgeChunkTaskEntity> {

    @Select("SELECT COALESCE(MAX(version_no), 0) + 1 FROM lingxi_core.ai_knowledge_chunk_task WHERE document_id = #{documentId}")
    int nextVersion(@Param("documentId") Long documentId);

    @Update("""
            UPDATE lingxi_core.ai_knowledge_chunk_task
               SET status = 'PARSING', progress = 5, started_at = #{startedAt},
                   error_code = NULL, error_message = NULL, updated_at = CURRENT_TIMESTAMP
             WHERE id = #{id} AND status = 'PENDING'
            """)
    int claimParsing(@Param("id") Long id, @Param("startedAt") OffsetDateTime startedAt);

    @Update("""
            UPDATE lingxi_core.ai_knowledge_chunk_task
               SET status = 'CHUNKING', progress = 20, updated_at = CURRENT_TIMESTAMP
             WHERE id = #{id} AND status = 'PARSING'
            """)
    int markChunking(@Param("id") Long id);

    @Update("""
            UPDATE lingxi_core.ai_knowledge_chunk_task
               SET status = 'VECTORIZING', progress = 55, updated_at = CURRENT_TIMESTAMP
             WHERE id = #{id} AND status = 'CHUNKING'
            """)
    int markVectorizing(@Param("id") Long id);

    @Select("""
            SELECT id FROM lingxi_core.ai_knowledge_chunk_task
             WHERE status = #{status} ORDER BY created_at, id LIMIT 20
            """)
    List<Long> findIdsByStatus(@Param("status") String status);

    /** 将服务异常退出后遗留的执行中任务恢复到对应等待状态。 */
    @Update("""
            UPDATE lingxi_core.ai_knowledge_chunk_task
               SET status = 'PENDING',
                   progress = 0,
                   vectorized_count = 0,
                   updated_at = CURRENT_TIMESTAMP
             WHERE status IN ('PARSING', 'CHUNKING', 'VECTORIZING') AND updated_at < #{deadline}
            """)
    int resetStale(@Param("deadline") OffsetDateTime deadline);
}

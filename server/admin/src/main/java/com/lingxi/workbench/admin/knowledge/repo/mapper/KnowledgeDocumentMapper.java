package com.lingxi.workbench.admin.knowledge.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeDocumentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 知识库文档数据访问层。 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentEntity> {

    /**
     * 原子抢占文档处理权并保存本次分块配置，防止重复提交。
     */
    @Update("""
            UPDATE lingxi_core.ai_knowledge_document
               SET status = 'RUNNING',
                   chunk_strategy = #{strategy},
                   chunk_size = #{chunkSize},
                   chunk_overlap = #{chunkOverlap},
                   error_code = NULL,
                   error_message = NULL,
                   updated_by = #{operatorId},
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{id} AND status <> 'RUNNING'
            """)
    int canClaimProcessing(@Param("id") Long id,
                        @Param("strategy") String strategy,
                        @Param("chunkSize") Integer chunkSize,
                        @Param("chunkOverlap") Integer chunkOverlap,
                        @Param("operatorId") Long operatorId);
}

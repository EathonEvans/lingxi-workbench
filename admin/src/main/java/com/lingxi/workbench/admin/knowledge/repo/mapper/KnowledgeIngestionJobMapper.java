package com.lingxi.workbench.admin.knowledge.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeIngestionJobEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

/** 文档解析任务数据访问层。 */
@Mapper
public interface KnowledgeIngestionJobMapper extends BaseMapper<KnowledgeIngestionJobEntity> {

    /** 原子抢占等待中的任务，避免事件线程和恢复线程重复执行同一文档。 */
    @Update("""
            UPDATE lingxi_core.ai_knowledge_ingestion_job
               SET status = 'RUNNING', progress = 10, started_at = #{startedAt},
                   finished_at = NULL, updated_at = CURRENT_TIMESTAMP
             WHERE id = #{id} AND status = 'PENDING'
            """)
    int claim(@Param("id") Long id, @Param("startedAt") OffsetDateTime startedAt);

    /** 每次只领取有限数量，避免恢复扫描占满解析线程。 */
    @Select("""
            SELECT id
              FROM lingxi_core.ai_knowledge_ingestion_job
             WHERE status = 'PENDING'
             ORDER BY created_at, id
             LIMIT 20
            """)
    List<Long> findPendingIds();

    /** 服务异常退出遗留的超时任务重新进入等待状态。 */
    @Update("""
            UPDATE lingxi_core.ai_knowledge_ingestion_job
               SET status = 'PENDING', progress = 0, started_at = NULL,
                   updated_at = CURRENT_TIMESTAMP
             WHERE status = 'RUNNING' AND started_at < #{deadline}
            """)
    int resetStale(@Param("deadline") OffsetDateTime deadline);
}

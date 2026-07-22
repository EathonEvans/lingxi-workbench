package com.lingxi.workbench.admin.knowledge.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeJobStatus;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeIngestionJobEntity;
import com.lingxi.workbench.admin.knowledge.repo.mapper.KnowledgeIngestionJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 文档解析任务数据仓库。
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeIngestionJobRepo {
    private final KnowledgeIngestionJobMapper mapper;

    /**
     * 按主键查询解析任务。
     */
    public Optional<KnowledgeIngestionJobEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /**
     * 原子抢占等待中的任务，返回是否抢占成功。
     */
    public boolean claim(Long id, OffsetDateTime startedAt) {
        return mapper.claim(id, startedAt) == 1;
    }

    /**
     * 查询所有等待执行的解析任务ID列表。
     */
    public List<Long> findPendingIds() {
        return mapper.findPendingIds();
    }

    /**
     * 复位超时的遗留任务为等待状态。
     */
    public void resetStale(OffsetDateTime deadline) {
        mapper.resetStale(deadline);
    }

    /**
     * 检查文档是否已有等待或执行中的解析任务。
     */
    public boolean existsRunningByDocumentId(Long documentId) {
        return mapper.exists(Wrappers.<KnowledgeIngestionJobEntity>lambdaQuery()
                .eq(KnowledgeIngestionJobEntity::getDocumentId, documentId)
                .in(KnowledgeIngestionJobEntity::getStatus,
                        KnowledgeJobStatus.PENDING,
                        KnowledgeJobStatus.RUNNING));
    }

    /**
     * 新增或更新解析任务。
     */
    public void save(KnowledgeIngestionJobEntity entity) {
        if (Objects.isNull(entity.getId())) mapper.insert(entity);
        else mapper.updateById(entity);
    }
}

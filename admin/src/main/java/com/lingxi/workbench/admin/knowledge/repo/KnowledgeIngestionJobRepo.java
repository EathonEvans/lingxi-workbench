package com.lingxi.workbench.admin.knowledge.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeJobStatus;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeIngestionJobEntity;
import com.lingxi.workbench.admin.knowledge.repo.mapper.KnowledgeIngestionJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.List;

/** 文档解析任务数据仓库。 */
@Repository
@RequiredArgsConstructor
public class KnowledgeIngestionJobRepo {
    private final KnowledgeIngestionJobMapper mapper;

    public Optional<KnowledgeIngestionJobEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public boolean claim(Long id, OffsetDateTime startedAt) {
        return mapper.claim(id, startedAt) == 1;
    }

    public List<Long> findPendingIds() {
        return mapper.findPendingIds();
    }

    public void resetStale(OffsetDateTime deadline) {
        mapper.resetStale(deadline);
    }

    public boolean existsRunningByDocumentId(Long documentId) {
        return mapper.exists(Wrappers.<KnowledgeIngestionJobEntity>lambdaQuery()
                .eq(KnowledgeIngestionJobEntity::getDocumentId, documentId)
                .in(KnowledgeIngestionJobEntity::getStatus, KnowledgeJobStatus.PENDING, KnowledgeJobStatus.RUNNING));
    }

    public void save(KnowledgeIngestionJobEntity entity) {
        if (Objects.isNull(entity.getId())) mapper.insert(entity);
        else mapper.updateById(entity);
    }
}

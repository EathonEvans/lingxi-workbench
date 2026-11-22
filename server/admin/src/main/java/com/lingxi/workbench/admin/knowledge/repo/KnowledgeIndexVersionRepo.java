package com.lingxi.workbench.admin.knowledge.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeIndexVersionEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeIndexVersionTaskEntity;
import com.lingxi.workbench.admin.knowledge.repo.mapper.KnowledgeIndexVersionMapper;
import com.lingxi.workbench.admin.knowledge.repo.mapper.KnowledgeIndexVersionTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 知识索引版本及其分块任务引用仓储。 */
@Repository
@RequiredArgsConstructor
public class KnowledgeIndexVersionRepo {
    private final KnowledgeIndexVersionMapper versionMapper;
    private final KnowledgeIndexVersionTaskMapper taskMapper;

    public Optional<KnowledgeIndexVersionEntity> findByBaseAndChecksum(Long knowledgeBaseId, String checksum) {
        return Optional.ofNullable(versionMapper.selectOne(Wrappers.<KnowledgeIndexVersionEntity>lambdaQuery()
                .eq(KnowledgeIndexVersionEntity::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KnowledgeIndexVersionEntity::getContentChecksum, checksum)));
    }

    public int nextVersion(Long knowledgeBaseId) {
        return versionMapper.nextVersion(knowledgeBaseId);
    }

    public void insert(KnowledgeIndexVersionEntity version, List<KnowledgeIndexVersionTaskEntity> tasks) {
        versionMapper.insert(version);
        for (KnowledgeIndexVersionTaskEntity task : tasks) {
            task.setIndexVersionId(version.getId());
            taskMapper.insert(task);
        }
    }

    public boolean existsByTaskId(Long taskId) {
        return taskMapper.exists(Wrappers.<KnowledgeIndexVersionTaskEntity>lambdaQuery()
                .eq(KnowledgeIndexVersionTaskEntity::getChunkTaskId, taskId));
    }

    public boolean existsByDocumentId(Long documentId) {
        return taskMapper.exists(Wrappers.<KnowledgeIndexVersionTaskEntity>lambdaQuery()
                .eq(KnowledgeIndexVersionTaskEntity::getDocumentId, documentId));
    }

    public boolean existsByKnowledgeBaseId(Long knowledgeBaseId) {
        return versionMapper.exists(Wrappers.<KnowledgeIndexVersionEntity>lambdaQuery()
                .eq(KnowledgeIndexVersionEntity::getKnowledgeBaseId, knowledgeBaseId));
    }
}

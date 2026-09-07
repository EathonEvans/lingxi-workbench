package com.lingxi.workbench.admin.knowledge.repo;

import com.lingxi.workbench.admin.knowledge.model.bo.KnowledgeAccessGrant;
import com.lingxi.workbench.admin.knowledge.repo.mapper.KnowledgeAccessMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 授权全量替换由调用方事务与知识库行锁保护。 */
@Repository
@RequiredArgsConstructor
public class KnowledgeAccessRepo {
    private final KnowledgeAccessMapper mapper;

    public List<KnowledgeAccessGrant> findByBaseId(Long id) {
        return mapper.selectByBaseId(id);
    }

    public void replace(Long id, List<KnowledgeAccessGrant> grants) {
        mapper.deleteByBaseId(id);
        if (!grants.isEmpty()) {
            mapper.insertAll(id, grants);
        }
    }
}

package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.RuntimeIntentTreeVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 运行端读取 ApplicationVersion 锁定的意图树版本。 */
@Repository
@RequiredArgsConstructor
public class IntentTreeRuntimeRepo {

    private final RuntimeIntentTreeVersionMapper mapper;

    public Optional<RuntimeIntentTreeVersionEntity> findVersion(Long intentTreeId, Long versionId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<RuntimeIntentTreeVersionEntity>lambdaQuery()
                        .eq(RuntimeIntentTreeVersionEntity::getIntentTreeId, intentTreeId)
                        .eq(RuntimeIntentTreeVersionEntity::getId, versionId)));
    }
}

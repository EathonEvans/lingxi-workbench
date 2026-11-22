package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.RuntimeIntentTreeVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 运行端只读的意图树版本查询 Repo。
 *
 * <p>负责按意图树身份和版本双主键查询已发布的不可变版本快照。
 * 同时校验版本确实归属于指定的意图树，防止跨树引用。</p>
 */
@Repository
@RequiredArgsConstructor
public class IntentTreeRuntimeRepo {

    private final RuntimeIntentTreeVersionMapper mapper;

    /**
     * 按意图树 ID 和版本 ID 精确查询已发布版本。
     *
     * <p>同时校验版本确实归属于指定的意图树，返回 Optional 表示可能存在或缺失。</p>
     *
     * @param intentTreeId 意图树身份主键
     * @param versionId    版本主键，来自 ApplicationVersion 快照中锁定的 intentTreeVersionId
     * @return 包含定义快照、Embedding 模型 ID 等运行时必要字段的实体，未找到时为空
     */
    public Optional<RuntimeIntentTreeVersionEntity> findVersion(Long intentTreeId, Long versionId) {
        return Optional.ofNullable(
                mapper.selectOne(new LambdaQueryWrapper<RuntimeIntentTreeVersionEntity>()
                        .eq(RuntimeIntentTreeVersionEntity::getId, versionId)
                        .eq(RuntimeIntentTreeVersionEntity::getIntentTreeId, intentTreeId)));
    }
}
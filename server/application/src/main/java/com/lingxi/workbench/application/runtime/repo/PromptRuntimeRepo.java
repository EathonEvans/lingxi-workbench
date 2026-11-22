package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.model.entity.RuntimePromptEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimePromptVersionEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.RuntimePromptMapper;
import com.lingxi.workbench.application.runtime.repo.mapper.RuntimePromptVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 已发布 PromptVersion 的运行端只读仓储。 */
@Repository
@RequiredArgsConstructor
public class PromptRuntimeRepo {

    private static final String PUBLISHED_STATUS = "PUBLISHED";
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final RuntimePromptVersionMapper versionMapper;
    private final RuntimePromptMapper promptMapper;

    /**
     * 只读取指定的已发布版本及其启用主记录，禁止在运行时回退到草稿或其他版本。
     */
    public Optional<RuntimePromptVersionEntity> findPublishedVersion(Long versionId) {
        RuntimePromptVersionEntity version = versionMapper.selectOne(
                Wrappers.<RuntimePromptVersionEntity>lambdaQuery()
                        .eq(RuntimePromptVersionEntity::getId, versionId)
                        .eq(RuntimePromptVersionEntity::getStatus, PUBLISHED_STATUS));
        if (version == null) {
            return Optional.empty();
        }
        RuntimePromptEntity prompt = promptMapper.selectOne(
                Wrappers.<RuntimePromptEntity>lambdaQuery()
                        .eq(RuntimePromptEntity::getId, version.getPromptId())
                        .eq(RuntimePromptEntity::getStatus, ACTIVE_STATUS));
        if (prompt == null) {
            return Optional.empty();
        }
        version.setRole(prompt.getRole());
        return Optional.of(version);
    }

    /**
     * 按稳定 Prompt 编码读取最新已发布版本，供平台内置补充任务（如回答建议）解析运行时 Prompt。
     *
     * <p>版本随重新发布自然演进，运行端始终读取最新已发布版本，不回退草稿或历史版本。</p>
     */
    public Optional<RuntimePromptVersionEntity> findLatestPublishedByPromptCode(String promptCode) {
        RuntimePromptEntity prompt = promptMapper.selectOne(
                Wrappers.<RuntimePromptEntity>lambdaQuery()
                        .eq(RuntimePromptEntity::getCode, promptCode)
                        .eq(RuntimePromptEntity::getStatus, ACTIVE_STATUS));
        if (prompt == null) {
            return Optional.empty();
        }
        RuntimePromptVersionEntity version = versionMapper.selectOne(
                Wrappers.<RuntimePromptVersionEntity>lambdaQuery()
                        .eq(RuntimePromptVersionEntity::getPromptId, prompt.getId())
                        .eq(RuntimePromptVersionEntity::getStatus, PUBLISHED_STATUS)
                        .orderByDesc(RuntimePromptVersionEntity::getVersionNo)
                        .last("LIMIT 1"));
        if (version == null) {
            return Optional.empty();
        }
        version.setRole(prompt.getRole());
        return Optional.of(version);
    }
}

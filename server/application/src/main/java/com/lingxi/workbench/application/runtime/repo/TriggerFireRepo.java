package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.model.entity.TriggerFireEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.TriggerFireMapper;
import com.lingxi.workbench.framework.convention.PageQuery;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 触发器到期触发记录数据访问语义封装。 */
@Repository
@RequiredArgsConstructor
public class TriggerFireRepo {

    private final TriggerFireMapper mapper;

    /** 按触发时间倒序分页查询触发器的运行历史。 */
    public PageResult<TriggerFireEntity> findPageByTrigger(Long triggerId, PageQuery query) {
        IPage<TriggerFireEntity> page = mapper.selectPage(
                query.toPage(),
                Wrappers.<TriggerFireEntity>lambdaQuery()
                        .eq(TriggerFireEntity::getTriggerId, triggerId)
                        .orderByDesc(TriggerFireEntity::getCreatedAt)
                        .orderByDesc(TriggerFireEntity::getId));
        return PageResult.convert(page);
    }

    /** 读取触发记录。 */
    public Optional<TriggerFireEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /** 新建触发记录。 */
    public void insert(TriggerFireEntity entity) {
        mapper.insert(entity);
    }

    /** 消费侧幂等抢占 RUNNING，返回 true 表示本次消费获得执行权。 */
    public boolean claimRunning(Long id, String runId) {
        return mapper.claimRunning(id, runId) > 0;
    }

    /** 推进终态，仅允许从 FIRED/RUNNING 推进。 */
    public boolean finish(Long id, String targetStatus, String skipReason, String errorMessage) {
        return mapper.finish(id, targetStatus, skipReason, errorMessage) > 0;
    }

    /** 成功终态并回填关联运行标识，返回 true 表示推进成功。 */
    public boolean markSucceeded(Long id, String runId) {
        return mapper.markSucceeded(id, runId) > 0;
    }

    /** 对账扫描长时间无终态的触发记录。 */
    public List<TriggerFireEntity> findStale(OffsetDateTime staleBefore, int limit) {
        return mapper.selectStale(staleBefore, limit);
    }
}

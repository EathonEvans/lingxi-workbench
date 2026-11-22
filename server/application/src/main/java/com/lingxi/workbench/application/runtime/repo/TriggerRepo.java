package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.model.entity.TriggerEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.TriggerMapper;
import com.lingxi.workbench.framework.convention.PageQuery;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 定时触发器数据访问语义封装。 */
@Repository
@RequiredArgsConstructor
public class TriggerRepo {

    private final TriggerMapper mapper;

    /** 按创建时间倒序分页查询本人未删除触发器。 */
    public PageResult<TriggerEntity> findPage(Long userId, PageQuery query) {
        IPage<TriggerEntity> page = mapper.selectPage(
                query.toPage(),
                Wrappers.<TriggerEntity>lambdaQuery()
                        .eq(TriggerEntity::getUserId, userId)
                        .isNull(TriggerEntity::getDeletedAt)
                        .orderByDesc(TriggerEntity::getUpdatedAt)
                        .orderByDesc(TriggerEntity::getId));
        return PageResult.convert(page);
    }

    /** 查询本人未删除的触发器。 */
    public Optional<TriggerEntity> findOwnedById(Long id, Long userId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<TriggerEntity>lambdaQuery()
                        .eq(TriggerEntity::getId, id)
                        .eq(TriggerEntity::getUserId, userId)
                        .isNull(TriggerEntity::getDeletedAt)));
    }

    /** 按主键读取未删除触发器，供事务内协同与对账使用。 */
    public Optional<TriggerEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<TriggerEntity>lambdaQuery()
                        .eq(TriggerEntity::getId, id)
                        .isNull(TriggerEntity::getDeletedAt)));
    }

    /** 扫描到期的调度中触发器。 */
    public java.util.List<TriggerEntity> findDue(OffsetDateTime now, int limit) {
        return mapper.selectDue(now, limit);
    }

    /** 抢占式推进下次触发时间，返回 true 表示抢占成功。 */
    public boolean claimNextFire(Long id, OffsetDateTime expectedNextFireAt, OffsetDateTime nextFireAt) {
        return mapper.claimNextFire(id, expectedNextFireAt, nextFireAt) > 0;
    }

    /** 记录一次运行结果。 */
    public void recordRunResult(Long id, String runId, String runStatus, OffsetDateTime runAt, boolean succeeded) {
        mapper.recordRunResult(id, runId, runStatus, runAt, succeeded);
    }

    /** 达到连续失败阈值后自动暂停，返回 true 表示本次调用完成了暂停。 */
    public boolean autoPauseOnFailureThreshold(Long id, int threshold, String pauseReason) {
        return mapper.autoPauseOnFailureThreshold(id, threshold, pauseReason) > 0;
    }

    /** 用户恢复调度，返回 true 表示状态推进成功。 */
    public boolean resume(Long id, OffsetDateTime nextFireAt) {
        return mapper.resume(id, nextFireAt) > 0;
    }

    /** 统计用户调度中的触发器数量。 */
    public long countActiveByUser(Long userId) {
        return mapper.countActiveByUser(userId);
    }

    /** 统计用户自指定时间起已触发的次数。 */
    public long countFiredSince(Long userId, OffsetDateTime dayStart) {
        return mapper.countFiredSince(userId, dayStart);
    }

    /** 更新触发器的可编辑字段（保存实体快照）。 */
    public void save(TriggerEntity entity) {
        mapper.updateById(entity);
    }

    /** 新建触发器。 */
    public void insert(TriggerEntity entity) {
        mapper.insert(entity);
    }
}

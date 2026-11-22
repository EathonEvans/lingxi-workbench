package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.common.enums.CapabilityInvocationStatus;
import com.lingxi.workbench.application.runtime.model.entity.CapabilityInvocationEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.CapabilityInvocationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Tool 调用意图及实际执行结果持久化语义。 */
@Repository
@RequiredArgsConstructor
public class CapabilityInvocationRepo {

    private final CapabilityInvocationMapper mapper;

    public void insert(CapabilityInvocationEntity invocation) {
        mapper.insert(invocation);
    }

    public boolean bindInteraction(Long id, Long interactionId) {
        CapabilityInvocationEntity update = new CapabilityInvocationEntity();
        update.setInteractionId(interactionId);
        return mapper.update(update, Wrappers.<CapabilityInvocationEntity>lambdaUpdate()
                .eq(CapabilityInvocationEntity::getId, id)
                .isNull(CapabilityInvocationEntity::getInteractionId)) == 1;
    }

    public Optional<CapabilityInvocationEntity> findByInteractionId(Long interactionId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<CapabilityInvocationEntity>lambdaQuery()
                .eq(CapabilityInvocationEntity::getInteractionId, interactionId)));
    }

    public Optional<CapabilityInvocationEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public boolean changeStatus(Long id, CapabilityInvocationStatus expected, CapabilityInvocationStatus target,
                                String idempotencyKey, String resultDigest, String errorCode) {
        CapabilityInvocationEntity update = new CapabilityInvocationEntity();
        update.setStatus(target);
        update.setIdempotencyKey(idempotencyKey);
        update.setResultDigest(resultDigest);
        update.setErrorCode(errorCode);
        OffsetDateTime now = OffsetDateTime.now();
        if (target == CapabilityInvocationStatus.RUNNING) {
            update.setStartedAt(now);
        }
        if (target == CapabilityInvocationStatus.RECONCILIATION_PENDING) {
            update.setNextReconcileAt(now);
        }
        if (target == CapabilityInvocationStatus.SUCCEEDED || target == CapabilityInvocationStatus.FAILED
                || target == CapabilityInvocationStatus.RECONCILIATION_PENDING
                || target == CapabilityInvocationStatus.REJECTED || target == CapabilityInvocationStatus.CANCELLED
                || target == CapabilityInvocationStatus.EXPIRED) {
            update.setFinishedAt(now);
        }
        return mapper.update(update, Wrappers.<CapabilityInvocationEntity>lambdaUpdate()
                .eq(CapabilityInvocationEntity::getId, id)
                .eq(CapabilityInvocationEntity::getStatus, expected)) == 1;
    }

    /** 查询已到达下次对账时间的调用意图，调用方必须再以 CAS 领取具体尝试。 */
    public List<CapabilityInvocationEntity> findReconciliationCandidates(OffsetDateTime now, int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Tool对账批次大小必须在1到1000之间");
        }
        return mapper.selectList(Wrappers.<CapabilityInvocationEntity>lambdaQuery()
                .eq(CapabilityInvocationEntity::getStatus, CapabilityInvocationStatus.RECONCILIATION_PENDING)
                .isNotNull(CapabilityInvocationEntity::getNextReconcileAt)
                .le(CapabilityInvocationEntity::getNextReconcileAt, now)
                .orderByAsc(CapabilityInvocationEntity::getNextReconcileAt)
                .orderByAsc(CapabilityInvocationEntity::getId)
                .last("LIMIT " + limit));
    }

    /** 用一次有界 CAS 占用只读查询额度；领取实例崩溃时租约到期后可由其他实例继续查询。 */
    public boolean claimReconciliationAttempt(Long id, int maxAttempts, OffsetDateTime leaseExpiresAt) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Tool对账最大尝试次数必须大于0");
        }
        CapabilityInvocationEntity update = new CapabilityInvocationEntity();
        update.setNextReconcileAt(leaseExpiresAt);
        return mapper.update(update, Wrappers.<CapabilityInvocationEntity>lambdaUpdate()
                .eq(CapabilityInvocationEntity::getId, id)
                .eq(CapabilityInvocationEntity::getStatus, CapabilityInvocationStatus.RECONCILIATION_PENDING)
                .lt(CapabilityInvocationEntity::getReconciliationAttempts, maxAttempts)
                .setSql("reconciliation_attempts = reconciliation_attempts + 1")) == 1;
    }

    /** 外部查询暂未给出确定结论时，按有限延迟安排下一次只读查询。 */
    public boolean rescheduleReconciliation(Long id, OffsetDateTime nextReconcileAt, String errorCode) {
        CapabilityInvocationEntity update = new CapabilityInvocationEntity();
        update.setNextReconcileAt(nextReconcileAt);
        update.setErrorCode(errorCode);
        return mapper.update(update, Wrappers.<CapabilityInvocationEntity>lambdaUpdate()
                .eq(CapabilityInvocationEntity::getId, id)
                .eq(CapabilityInvocationEntity::getStatus, CapabilityInvocationStatus.RECONCILIATION_PENDING)) == 1;
    }

    /** 没有安全的自动下一步时停止调度，保留待对账事实供人工处理。 */
    public boolean suspendReconciliation(Long id, String errorCode) {
        CapabilityInvocationEntity update = new CapabilityInvocationEntity();
        update.setErrorCode(errorCode);
        return mapper.update(update, Wrappers.<CapabilityInvocationEntity>lambdaUpdate()
                .eq(CapabilityInvocationEntity::getId, id)
                .eq(CapabilityInvocationEntity::getStatus, CapabilityInvocationStatus.RECONCILIATION_PENDING)
                .set(CapabilityInvocationEntity::getNextReconcileAt, null)) == 1;
    }

    /** 对账得出确定结论后收敛调用意图，终态必须继续由数据库 CAS 决定。 */
    public boolean finishReconciliation(Long id, CapabilityInvocationStatus target, String resultDigest, String errorCode) {
        if (target != CapabilityInvocationStatus.SUCCEEDED && target != CapabilityInvocationStatus.FAILED) {
            throw new IllegalArgumentException("Tool对账只能收敛为成功或失败");
        }
        CapabilityInvocationEntity update = new CapabilityInvocationEntity();
        update.setStatus(target);
        update.setResultDigest(resultDigest);
        update.setErrorCode(errorCode);
        update.setFinishedAt(OffsetDateTime.now());
        return mapper.update(update, Wrappers.<CapabilityInvocationEntity>lambdaUpdate()
                .eq(CapabilityInvocationEntity::getId, id)
                .eq(CapabilityInvocationEntity::getStatus, CapabilityInvocationStatus.RECONCILIATION_PENDING)
                .set(CapabilityInvocationEntity::getNextReconcileAt, null)) == 1;
    }

}

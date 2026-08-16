package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.common.enums.CapabilityInvocationStatus;
import com.lingxi.workbench.application.runtime.model.entity.CapabilityInvocationEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.CapabilityInvocationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
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
        if (target == CapabilityInvocationStatus.RUNNING) {
            update.setStartedAt(OffsetDateTime.now());
        }
        if (target == CapabilityInvocationStatus.SUCCEEDED || target == CapabilityInvocationStatus.FAILED
                || target == CapabilityInvocationStatus.REJECTED || target == CapabilityInvocationStatus.CANCELLED
                || target == CapabilityInvocationStatus.EXPIRED) {
            update.setFinishedAt(OffsetDateTime.now());
        }
        return mapper.update(update, Wrappers.<CapabilityInvocationEntity>lambdaUpdate()
                .eq(CapabilityInvocationEntity::getId, id)
                .eq(CapabilityInvocationEntity::getStatus, expected)) == 1;
    }

}

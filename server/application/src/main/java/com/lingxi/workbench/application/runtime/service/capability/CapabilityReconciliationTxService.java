package com.lingxi.workbench.application.runtime.service.capability;

import cn.hutool.crypto.digest.DigestUtil;
import com.lingxi.workbench.application.runtime.common.enums.CapabilityInvocationStatus;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionRunStatus;
import com.lingxi.workbench.application.runtime.model.entity.CapabilityInvocationEntity;
import com.lingxi.workbench.application.runtime.repo.CapabilityInvocationRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionCheckpointRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionRunRepo;
import com.lingxi.workbench.application.runtime.repo.InteractionSessionRepo;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/** Tool 对账结论写回调用意图、审批检查点和顶级 Run 的短事务边界。 */
@Service
@RequiredArgsConstructor
public class CapabilityReconciliationTxService {

    private final CapabilityInvocationRepo invocationRepo;
    private final ExecutionCheckpointRepo checkpointRepo;
    private final InteractionSessionRepo interactionRepo;
    private final ExecutionRunRepo runRepo;

    /** 外部系统确认副作用已成功，但模型循环无法安全续跑，Run 以 PARTIAL 收口。 */
    @Transactional
    public boolean completeSucceeded(Long invocationId, String result) {
        CapabilityInvocationEntity invocation = requirePending(invocationId);
        if (!invocationRepo.finishReconciliation(invocationId, CapabilityInvocationStatus.SUCCEEDED,
                DigestUtil.sha256Hex(result), null)) {
            return false;
        }
        consumeApproval(invocation);
        if (!runRepo.changeStatus(invocation.getRunId(), ExecutionRunStatus.RECONCILIATION_PENDING,
                ExecutionRunStatus.PARTIAL, "TOOL_RECONCILED", OffsetDateTime.now())) {
            throw conflict("对账成功但Application运行状态已变化");
        }
        return true;
    }

    /** 外部系统确认原操作未生效，保留失败结论，不重新执行已批准的原 Tool。 */
    @Transactional
    public boolean completeNotApplied(Long invocationId) {
        CapabilityInvocationEntity invocation = requirePending(invocationId);
        if (!invocationRepo.finishReconciliation(invocationId, CapabilityInvocationStatus.FAILED,
                null, "TOOL_EFFECT_NOT_APPLIED")) {
            return false;
        }
        consumeApproval(invocation);
        if (!runRepo.changeStatus(invocation.getRunId(), ExecutionRunStatus.RECONCILIATION_PENDING,
                ExecutionRunStatus.FAILED, "TOOL_EFFECT_NOT_APPLIED", OffsetDateTime.now())) {
            throw conflict("对账失败但Application运行状态已变化");
        }
        return true;
    }

    private CapabilityInvocationEntity requirePending(Long invocationId) {
        CapabilityInvocationEntity invocation = invocationRepo.findById(invocationId)
                .orElseThrow(() -> conflict("待对账Tool调用不存在"));
        if (invocation.getStatus() != CapabilityInvocationStatus.RECONCILIATION_PENDING) {
            throw conflict("待对账Tool调用状态已变化");
        }
        return invocation;
    }

    private void consumeApproval(CapabilityInvocationEntity invocation) {
        if (!checkpointRepo.consume(invocation.getCheckpointId(), OffsetDateTime.now())) {
            throw conflict("待对账Tool检查点状态已变化");
        }
        if (!interactionRepo.complete(invocation.getInteractionId())) {
            throw conflict("待对账Tool审批交互状态已变化");
        }
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(HttpStatusEnum.CONFLICT, message);
    }
}

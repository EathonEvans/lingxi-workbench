package com.lingxi.workbench.application.runtime.service.capability;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionRunStatus;
import com.lingxi.workbench.application.runtime.model.entity.CapabilityInvocationEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionRunEntity;
import com.lingxi.workbench.application.runtime.repo.CapabilityInvocationRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionRunRepo;
import com.lingxi.workbench.application.runtime.service.execution.RedisLockExecutor;
import com.lingxi.workbench.application.runtime.service.tool.ToolRuntimeService;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolReconciliationRequest;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolReconciliationResult;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolReconciliationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 多实例下按幂等键查询外部 Tool 结果的有限任务领取器。
 *
 * <p>Redis 锁只保护数据库领取；远端查询不在锁或事务内执行。查询本身必须只读且可重复，
 * 最终状态仍由 PostgreSQL 条件更新决定。达到上限或需要补偿时停止自动调度并保留人工对账事实。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CapabilityReconciliationService {

    private static final Duration CLAIM_LOCK_WAIT = Duration.ZERO;
    private static final Duration CLAIM_LOCK_LEASE = Duration.ofSeconds(10);
    private static final String QUERY_FAILED = "TOOL_RECONCILIATION_QUERY_FAILED";
    private static final String QUERY_PENDING = "TOOL_RECONCILIATION_PENDING";
    private static final String MANUAL_REVIEW = "TOOL_RECONCILIATION_MANUAL_REVIEW";
    private static final String MANUAL_COMPENSATION = "TOOL_RECONCILIATION_MANUAL_COMPENSATION";

    private final CapabilityInvocationRepo invocationRepo;
    private final ExecutionRunRepo runRepo;
    private final ToolRuntimeService toolRuntimeService;
    private final RedisLockExecutor redisLockExecutor;
    private final CapabilityReconciliationTxService reconciliationTxService;
    private final CapabilityReconciliationProperties properties;

    @Scheduled(fixedDelayString = "${lingxi.runtime.reconciliation.delay:1m}")
    void scheduledReconcile() {
        if (!properties.isEnabled()) {
            return;
        }
        reconcile();
    }

    /** 仅供运维调度调用；每项外部查询均有幂等键、次数上限和数据库终态 CAS。 */
    public int reconcile() {
        OffsetDateTime now = OffsetDateTime.now();
        int reconciled = 0;
        for (CapabilityInvocationEntity candidate : invocationRepo.findReconciliationCandidates(now, properties.getBatchSize())) {
            if (claimAttempt(candidate, now)) {
                reconciled++;
                reconcileCandidate(candidate.getId());
            }
        }
        return reconciled;
    }

    private boolean claimAttempt(CapabilityInvocationEntity candidate, OffsetDateTime now) {
        Optional<Boolean> claimed = redisLockExecutor.tryExecute("lingxi:runtime:run:" + candidate.getRunId(),
                CLAIM_LOCK_WAIT, CLAIM_LOCK_LEASE,
                () -> invocationRepo.claimReconciliationAttempt(candidate.getId(), properties.getMaxAttempts(),
                        now.plus(properties.getClaimLease())));
        return claimed.orElse(false);
    }

    private void reconcileCandidate(Long invocationId) {
        CapabilityInvocationEntity invocation = invocationRepo.findById(invocationId).orElse(null);
        if (invocation == null || invocation.getNextReconcileAt() == null) {
            return;
        }
        ExecutionRunEntity run = runRepo.findByRunId(invocation.getRunId()).orElse(null);
        if (run == null || run.getStatus() != ExecutionRunStatus.RECONCILIATION_PENDING) {
            return;
        }
        AgentTool tool;
        try {
            tool = resolveReconciliableTool(invocation);
        } catch (RuntimeException exception) {
            invocationRepo.suspendReconciliation(invocationId, MANUAL_REVIEW);
            log.warn("Tool对账契约解析失败，invocationId={}, runId={}", invocationId, invocation.getRunId());
            return;
        }
        if (tool == null) {
            invocationRepo.suspendReconciliation(invocationId, MANUAL_REVIEW);
            return;
        }
        try {
            AgentToolReconciliationResult result = tool.reconciliation().orElseThrow().query(
                    new AgentToolReconciliationRequest(invocation.getIdempotencyKey(), invocation.getToolInput().toJSONString(),
                            executionContext(run)));
            applyResult(invocation, result);
        } catch (RuntimeException exception) {
            rescheduleOrSuspend(invocation, QUERY_FAILED);
            log.warn("Tool对账查询失败，invocationId={}, runId={}", invocationId, invocation.getRunId());
        }
    }

    private AgentTool resolveReconciliableTool(CapabilityInvocationEntity invocation) {
        List<AgentTool> tools = toolRuntimeService.resolveActiveTools(List.of(invocation.getToolCode()));
        if (tools.size() != 1) {
            return null;
        }
        AgentTool tool = tools.getFirst();
        if (!Objects.equals(tool.definition().getConfigurationHash(), invocation.getToolConfigurationHash())
                || tool.reconciliation().isEmpty()) {
            return null;
        }
        return tool;
    }

    private void applyResult(CapabilityInvocationEntity invocation, AgentToolReconciliationResult result) {
        switch (result.status()) {
            case SUCCEEDED -> reconciliationTxService.completeSucceeded(invocation.getId(), result.result());
            case NOT_APPLIED -> reconciliationTxService.completeNotApplied(invocation.getId());
            case PENDING -> rescheduleOrSuspend(invocation, QUERY_PENDING);
            case COMPENSATION_REQUIRED -> invocationRepo.suspendReconciliation(invocation.getId(), MANUAL_COMPENSATION);
        }
    }

    private void rescheduleOrSuspend(CapabilityInvocationEntity invocation, String errorCode) {
        int attempts = Objects.requireNonNullElse(invocation.getReconciliationAttempts(), 0);
        if (attempts >= properties.getMaxAttempts()) {
            invocationRepo.suspendReconciliation(invocation.getId(), MANUAL_REVIEW);
            return;
        }
        invocationRepo.rescheduleReconciliation(invocation.getId(), OffsetDateTime.now().plus(properties.getRetryDelay()), errorCode);
    }

    private static AgentToolExecutionContext executionContext(ExecutionRunEntity run) {
        return AgentToolExecutionContext.builder()
                .executionId(run.getRunId())
                .userId(run.getUserId())
                .conversationId(run.getConversationId())
                .assistantMessageId(run.getAssistantMessageId())
                .build();
    }
}

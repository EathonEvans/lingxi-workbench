package com.lingxi.workbench.application.runtime.model;

/**
 * @param runId    运行稳定标识
 * @param planNo   初始规划为1，后续重规划递增
 * @param decision 已通过严格校验的Planner决策
 */
public record ExecutionPlanSnapshotCommand(String runId, Integer planNo, ExecutionDecision decision) {
}

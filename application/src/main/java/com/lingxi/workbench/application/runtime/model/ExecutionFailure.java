package com.lingxi.workbench.application.runtime.model;

/**
 * 可安全持久化并展示给用户的执行失败信息。
 *
 * @param code          稳定失败编码
 * @param clientMessage 面向用户的可操作说明
 * @param retryable     是否适合由Planner基于现有上下文重规划
 */
public record ExecutionFailure(String code, String clientMessage, boolean retryable) {
}

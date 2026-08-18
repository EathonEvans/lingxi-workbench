package com.lingxi.workbench.agentruntime.model;

import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpoint;

/**
 * @param runRequest 当前进程重新加载冻结版本后构造的Runtime请求
 * @param checkpoint PostgreSQL读取的完整Runtime检查点
 * @param interactionId 已批准交互主键
 * @param fingerprint 客户端决定与检查点共同绑定的审批指纹
 */
public record AgentResumeRequest(AgentRunRequest runRequest, RuntimeCheckpoint checkpoint,
                                 Long interactionId, String fingerprint) {
}

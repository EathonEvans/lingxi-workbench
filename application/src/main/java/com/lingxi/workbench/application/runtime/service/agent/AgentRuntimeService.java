package com.lingxi.workbench.application.runtime.service.agent;

import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;

public interface AgentRuntimeService {

    /** 读取 Workflow 节点或意图叶子已经锁定的不可变 AgentVersion。 */
    AgentRuntimeSnapshot resolveVersion(Long agentId, Long agentVersionId);
}

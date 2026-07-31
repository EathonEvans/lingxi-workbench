package com.lingxi.workbench.application.runtime.service;

import com.lingxi.workbench.infra.ai.tool.AgentTool;

import java.util.List;

/**
 * 将控制面 Tool 定义与当前应用部署的 Java 执行器组合为运行时工具。
 */
public interface ToolRuntimeService {

    /** 按 Agent 快照中的顺序解析启用 Tool，任一侧缺失时拒绝执行。 */
    List<AgentTool> resolveActiveTools(List<String> toolCodes);
}

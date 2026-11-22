package com.lingxi.workbench.application.runtime.service.tool;

import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;

import java.util.List;

/**
 * 将控制面 Tool 定义与当前应用部署的 Java 执行器组合为运行时工具。
 */
public interface ToolRuntimeService {

    /** 按 Agent 快照中的顺序解析启用 Tool，任一侧缺失时拒绝执行。 */
    List<AgentTool> resolveActiveTools(List<String> toolCodes);

    /** 按Application发布快照锁定的Tool主键解析唯一执行器。 */
    default AgentTool resolveActiveTool(Long toolId) {
        throw new AgentToolException("当前Tool运行时不支持按主键解析执行器");
    }
}

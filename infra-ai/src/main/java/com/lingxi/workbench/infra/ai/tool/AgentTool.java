package com.lingxi.workbench.infra.ai.tool;

import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import org.springframework.ai.tool.ToolCallback;

import java.util.Optional;

/**
 * 可被 Agent 调用的完整工具。
 *
 * <p>该接口描述应用部署的执行器。控制面可在调用前使用数据库中的 Tool 定义
 * 装饰回调，使模型契约与执行代码保持模块解耦。</p>
 */
public interface AgentTool {

    /**
     * 返回执行器自描述信息，供启动校验和未装饰的运行场景使用。
     */
    AgentToolDefinition definition();

    /**
     * 返回交给 Spring AI 执行的工具回调。
     */
    ToolCallback callback();

    /**
     * 工具成功后是否应强制进入最终回答。
     *
     * <p>用于知识检索饱和等由工具结果才能确定的收敛条件，默认不改变 Agent 循环。</p>
     */
    default boolean shouldFinalizeAfterSuccess() {
        return false;
    }

    /**
     * 返回外部副作用结果查询能力；未显式声明时，运行端只能保留待人工对账状态。
     */
    default Optional<AgentToolReconciliation> reconciliation() {
        return Optional.empty();
    }
}

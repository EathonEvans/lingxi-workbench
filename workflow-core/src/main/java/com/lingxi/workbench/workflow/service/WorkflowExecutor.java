package com.lingxi.workbench.workflow.service;

import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.WorkflowExecutionRequest;
import com.lingxi.workbench.workflow.model.WorkflowExecutionPorts;
import com.lingxi.workbench.workflow.model.WorkflowExecutionResult;

/**
 * 工作流执行器边界，定义 DAG 工作流的执行契约。
 *
 * <p>上层 Application 只依赖该接口，不感知 LangGraph4j 等具体引擎实现。</p>
 */
public interface WorkflowExecutor {

    /**
     * 执行一次完整的工作流运行。
     *
     * <p>输入工作流定义和请求参数后，执行器负责编译、校验、按拓扑顺序调度各节点，
     * 收集每个节点的输入输出追踪记录，最终返回完整的执行结果。</p>
     *
     * @param definition 已验证的工作流定义，包含节点、连线及类型配置
     * @param request    单次执行请求，包含执行 ID、应用版本 ID 和初始输入变量
     * @return 工作流执行结果，包含输出变量和各节点追踪记录
     */
    WorkflowExecutionResult execute(WorkflowDefinition definition, WorkflowExecutionRequest request,
                                    WorkflowExecutionPorts ports);
}

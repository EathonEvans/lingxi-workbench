package com.lingxi.workbench.workflow.engine.langgraph;

import org.bsc.langgraph4j.state.AgentState;

import com.lingxi.workbench.workflow.model.WorkflowNodeTrace;

import java.util.List;
import java.util.Map;

import static com.lingxi.workbench.workflow.common.constants.WorkflowStateConstants.APPLICATION_VERSION_ID_KEY;
import static com.lingxi.workbench.workflow.common.constants.WorkflowStateConstants.EXECUTION_ID_KEY;
import static com.lingxi.workbench.workflow.common.constants.WorkflowStateConstants.TRACES_KEY;

/**
 * LangGraph4j 状态适配器，封装工作流运行期的全局状态读写。
 *
 * <p>负责从 AgentState 的 Map 通道中安全提取 executionId、applicationVersionId
 * 和节点追踪列表，对外屏蔽底层 LangGraph4j API。</p>
 */
final class WorkflowGraphState extends AgentState {

    WorkflowGraphState(Map<String, Object> initData) {
        super(initData);
    }

    /**
     * 获取本次工作流执行的唯一标识。
     *
     * @return 执行 ID 字符串
     * @throws IllegalStateException 状态中不存在 executionId 时抛出
     */
    String executionId() {
        return this.<String>value(EXECUTION_ID_KEY)
                .orElseThrow(() -> new IllegalStateException("Workflow executionId不存在"));
    }

    /**
     * 获取本次执行绑定的 Application 发布版本 ID。
     *
     * @return 发布版本 ID 字符串
     * @throws IllegalStateException 状态中不存在 applicationVersionId 时抛出
     */
    String applicationVersionId() {
        return this.<String>value(APPLICATION_VERSION_ID_KEY)
                .orElseThrow(() -> new IllegalStateException("Workflow applicationVersionId不存在"));
    }

    /**
     * 获取当前已收集的节点执行追踪记录。
     *
     * @return 追踪记录列表，无记录时返回空列表
     */
    List<WorkflowNodeTrace> traces() {
        return this.<List<WorkflowNodeTrace>>value(TRACES_KEY)
                .orElseGet(List::of);
    }
}

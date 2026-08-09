package com.lingxi.workbench.application.runtime.config;

import com.lingxi.workbench.workflow.engine.langgraph.LangGraphWorkflowExecutor;
import com.lingxi.workbench.workflow.service.AgentNodeInvoker;
import com.lingxi.workbench.workflow.service.IntentClassifierNodeInvoker;
import com.lingxi.workbench.workflow.service.WorkflowExecutor;
import com.lingxi.workbench.workflow.service.WorkflowRunListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Workflow 执行器运行端配置，将 AgentNodeInvoker 实现注入到图引擎中。 */
@Configuration
public class WorkflowRuntimeConfiguration {

    /** 使用 LangGraph 作为默认 Workflow 执行引擎。 */
    @Bean
    public WorkflowExecutor workflowExecutor(AgentNodeInvoker agentNodeInvoker, IntentClassifierNodeInvoker intentClassifierNodeInvoker, WorkflowRunListener runListener) {
        return new LangGraphWorkflowExecutor(agentNodeInvoker, intentClassifierNodeInvoker, runListener);
    }
}

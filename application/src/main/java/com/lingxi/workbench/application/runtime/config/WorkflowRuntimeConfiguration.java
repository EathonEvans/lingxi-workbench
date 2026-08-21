package com.lingxi.workbench.application.runtime.config;

import com.lingxi.workbench.workflow.engine.langgraph.LangGraphWorkflowExecutor;
import com.lingxi.workbench.workflow.service.WorkflowExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Workflow 图引擎配置，业务调用端口由每次执行显式绑定。 */
@Configuration
public class WorkflowRuntimeConfiguration {

    /** 使用 LangGraph 作为默认 Workflow 执行引擎。 */
    @Bean
    public WorkflowExecutor workflowExecutor() {
        return new LangGraphWorkflowExecutor();
    }
}

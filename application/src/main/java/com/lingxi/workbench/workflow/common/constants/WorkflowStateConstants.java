package com.lingxi.workbench.workflow.common.constants;

/**
 * Workflow 引擎运行状态中的内部变量键。
 *
 * <p>以下变量由执行器写入并由状态适配器读取，统一使用下划线前缀，
 * 避免与 Workflow Definition 声明的业务变量发生命名冲突。</p>
 */
public interface WorkflowStateConstants {

    /** 本次 Workflow 执行的唯一标识。 */
    String EXECUTION_ID_KEY = "_executionId";

    /** 本次执行锁定的 ApplicationVersion 标识。 */
    String APPLICATION_VERSION_ID_KEY = "_applicationVersionId";

    /** LangGraph4j 累加节点执行追踪记录使用的状态通道键。 */
    String TRACES_KEY = "_traces";
}

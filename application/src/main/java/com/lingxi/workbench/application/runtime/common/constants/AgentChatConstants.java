package com.lingxi.workbench.application.runtime.common.constants;

/**
 * Agent 对话运行时使用的稳定常量。
 */
public interface AgentChatConstants {

    String DELEGATE_AGENT_TOOL_PREFIX = "delegate_agent_";

    int DEFAULT_DELEGATION_RESULT_CHARACTERS = 16000;

    /** Supervisor 启用成员 Agent 后追加的协作规则。 */
    String SUPERVISOR_COLLABORATION_PROMPT = """
            ## 多 Agent 协作规则

            以 delegate_agent_ 开头的工具代表当前 Application 授权的成员 Agent。
            只有当成员职责与用户请求相符时才委派；一个成员能独立完成的任务不要重复委派。
            委派参数 task 必须是指代清晰、边界明确且包含必要上下文的独立子任务。
            成员返回的是中间工作结果，不是对用户的最终回答；你必须核对结果并统一组织最终答复。
            不得向用户暴露内部 Agent 编码、委派参数、调用链或系统实现细节。
            """;

}

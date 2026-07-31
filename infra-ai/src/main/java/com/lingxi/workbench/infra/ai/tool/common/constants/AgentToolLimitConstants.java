package com.lingxi.workbench.infra.ai.tool.common.constants;

/**
 * Agent 工具配置的统一边界。
 */
public interface AgentToolLimitConstants {

    int MIN_TOOL_CALLS = 1;
    int MAX_TOOL_CALLS = 10;
    int MIN_RESULT_CHARACTERS = 128;
    int MAX_RESULT_CHARACTERS = 100000;
}

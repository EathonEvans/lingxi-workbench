package com.lingxi.workbench.infra.ai.tool.common.constants;

/**
 * 系统计算器 Tool 的稳定编码和运算边界。
 */
public interface SystemCalculatorToolConstants {

    String TOOL_CODE = "system_calculator";

    String TOOL_DESCRIPTION = "对两个十进制数执行加、减、乘、除运算。只要用户要求进行明确数值的四则运算，即使模型能够心算，也必须调用本工具。";

    String OPERATOR_ADD = "ADD";

    String OPERATOR_SUBTRACT = "SUBTRACT";

    String OPERATOR_MULTIPLY = "MULTIPLY";

    String OPERATOR_DIVIDE = "DIVIDE";

    int DEFAULT_DIVISION_SCALE = 6;

    int MIN_DIVISION_SCALE = 0;

    int MAX_DIVISION_SCALE = 10;
}

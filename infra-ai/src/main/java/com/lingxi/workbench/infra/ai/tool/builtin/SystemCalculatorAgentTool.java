package com.lingxi.workbench.infra.ai.tool.builtin;

import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRequest;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.lingxi.workbench.infra.ai.tool.common.constants.SystemCalculatorToolConstants.DEFAULT_DIVISION_SCALE;
import static com.lingxi.workbench.infra.ai.tool.common.constants.SystemCalculatorToolConstants.MAX_DIVISION_SCALE;
import static com.lingxi.workbench.infra.ai.tool.common.constants.SystemCalculatorToolConstants.MIN_DIVISION_SCALE;
import static com.lingxi.workbench.infra.ai.tool.common.constants.SystemCalculatorToolConstants.OPERATOR_ADD;
import static com.lingxi.workbench.infra.ai.tool.common.constants.SystemCalculatorToolConstants.OPERATOR_DIVIDE;
import static com.lingxi.workbench.infra.ai.tool.common.constants.SystemCalculatorToolConstants.OPERATOR_MULTIPLY;
import static com.lingxi.workbench.infra.ai.tool.common.constants.SystemCalculatorToolConstants.OPERATOR_SUBTRACT;
import static com.lingxi.workbench.infra.ai.tool.common.constants.SystemCalculatorToolConstants.TOOL_CODE;
import static com.lingxi.workbench.infra.ai.tool.common.constants.SystemCalculatorToolConstants.TOOL_DESCRIPTION;
import static com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionMode.DETERMINISTIC_READ_ONLY;

/**
 * 执行两个十进制数之间基础四则运算的只读内置工具。
 *
 * <p>使用 {@link BigDecimal} 避免浮点数精度误差，不执行表达式字符串，
 * 从设计上杜绝表达式注入和任意代码执行。</p>
 */
@Component
public class SystemCalculatorAgentTool implements AgentTool {

    @Override
    public AgentToolDefinition definition() {
        return AgentToolDefinition.builder()
                .code(TOOL_CODE)
                .displayName("基础计算器")
                .description(TOOL_DESCRIPTION)
                .executionMode(DETERMINISTIC_READ_ONLY)
                .build();
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "leftOperand", Map.of("type", "number", "description", "左操作数"),
                        "operator", Map.of("type", "string", "description", "ADD、SUBTRACT、MULTIPLY、DIVIDE或+ - * /"),
                        "rightOperand", Map.of("type", "number", "description", "右操作数"),
                        "scale", Map.of("type", "integer", "minimum", 0, "maximum", 10,
                                "description", "除法小数位数，默认6")),
                "required", List.of("leftOperand", "operator", "rightOperand"),
                "additionalProperties", false);
    }

    @Override
    public String execute(AgentToolRequest request) {
        CalculatorInput input = JSON.parseObject(request.input(), CalculatorInput.class);
        return JSON.toJSONString(calculate(input));
    }

    /**
     * 校验模型生成的参数并执行确定性计算。
     */
    CalculatorOutput calculate(CalculatorInput input) {
        if (Objects.isNull(input)) {
            throw new AgentToolException("计算器参数不能为空");
        }
        BigDecimal leftOperand = requireOperand(input.getLeftOperand(), "leftOperand");
        BigDecimal rightOperand = requireOperand(input.getRightOperand(), "rightOperand");
        String operator = normalizeOperator(input.getOperator());
        int divisionScale = resolveDivisionScale(input.getScale());

        BigDecimal result = switch (operator) {
            case OPERATOR_ADD -> leftOperand.add(rightOperand);
            case OPERATOR_SUBTRACT -> leftOperand.subtract(rightOperand);
            case OPERATOR_MULTIPLY -> leftOperand.multiply(rightOperand);
            case OPERATOR_DIVIDE -> divide(leftOperand, rightOperand, divisionScale);
            default -> throw new AgentToolException("不支持的计算运算符：" + input.getOperator());
        };
        return CalculatorOutput.builder()
                .expression(leftOperand.toPlainString() + " " + operator + " " + rightOperand.toPlainString())
                .result(normalizeResult(result))
                .build();
    }

    private static BigDecimal requireOperand(BigDecimal operand, String parameterName) {
        if (Objects.isNull(operand)) {
            throw new AgentToolException("计算器缺少必填参数：" + parameterName);
        }
        return operand;
    }

    private static String normalizeOperator(String operator) {
        if (Objects.isNull(operator) || operator.isBlank()) {
            throw new AgentToolException("计算器缺少必填参数：operator");
        }
        return switch (operator.trim().toUpperCase(Locale.ROOT)) {
            case "+", OPERATOR_ADD -> OPERATOR_ADD;
            case "-", OPERATOR_SUBTRACT -> OPERATOR_SUBTRACT;
            case "*", "×", OPERATOR_MULTIPLY -> OPERATOR_MULTIPLY;
            case "/", "÷", OPERATOR_DIVIDE -> OPERATOR_DIVIDE;
            default -> throw new AgentToolException("不支持的计算运算符：" + operator);
        };
    }

    private static int resolveDivisionScale(Integer scale) {
        int resolvedScale = Objects.requireNonNullElse(scale, DEFAULT_DIVISION_SCALE);
        if (resolvedScale < MIN_DIVISION_SCALE || resolvedScale > MAX_DIVISION_SCALE) {
            throw new AgentToolException("除法小数位数必须在0到10之间");
        }
        return resolvedScale;
    }

    private static BigDecimal divide(BigDecimal leftOperand, BigDecimal rightOperand, int divisionScale) {
        if (BigDecimal.ZERO.compareTo(rightOperand) == 0) {
            throw new AgentToolException("除数不能为0");
        }
        return leftOperand.divide(rightOperand, divisionScale, RoundingMode.HALF_UP);
    }

    private static String normalizeResult(BigDecimal result) {
        return result.stripTrailingZeros().toPlainString();
    }

    /**
     * 计算参数。
     */
    @Data
    @NoArgsConstructor
    public static class CalculatorInput {

        private BigDecimal leftOperand;

        private String operator;

        private BigDecimal rightOperand;

        private Integer scale;
    }

    /**
     * 返回给模型的结构化计算结果。
     */
    @Getter
    @Builder
    public static class CalculatorOutput {

        private final String expression;

        private final String result;
    }
}

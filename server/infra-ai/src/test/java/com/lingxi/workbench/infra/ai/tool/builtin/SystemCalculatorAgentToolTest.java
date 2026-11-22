package com.lingxi.workbench.infra.ai.tool.builtin;

import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemCalculatorAgentToolTest {

    private final SystemCalculatorAgentTool tool = new SystemCalculatorAgentTool();

    @Test
    void shouldRequireCalculatorForExplicitArithmetic() {
        assertTrue(tool.definition().getDescription().contains("必须调用本工具"));
    }

    @Test
    void shouldExposeMultiParameterSchemaAndMultiplyDecimalNumbers() {
        JSONObject schema = new JSONObject(tool.inputSchema());

        assertTrue(schema.getJSONObject("properties").containsKey("leftOperand"));
        assertTrue(schema.getJSONObject("properties").containsKey("operator"));
        assertTrue(schema.getJSONObject("properties").containsKey("rightOperand"));

        String rawResult = tool.execute(request("""
                {"leftOperand":127.5,"operator":"MULTIPLY","rightOperand":8.6}
                """));
        JSONObject result = JSONObject.parseObject(rawResult);

        assertEquals("127.5 MULTIPLY 8.6", result.getString("expression"));
        assertEquals("1096.5", result.getString("result"));
    }

    @Test
    void shouldRoundDivisionUsingConfiguredScale() {
        SystemCalculatorAgentTool.CalculatorInput input = input("1", "DIVIDE", "3", 4);

        SystemCalculatorAgentTool.CalculatorOutput output = tool.calculate(input);

        assertEquals("0.3333", output.getResult());
    }

    @Test
    void shouldRejectDivisionByZero() {
        SystemCalculatorAgentTool.CalculatorInput input = input("10", "/", "0", null);

        AgentToolException exception = assertThrows(AgentToolException.class, () -> tool.calculate(input));

        assertEquals("除数不能为0", exception.getMessage());
    }

    @Test
    void shouldRejectUnsupportedOperator() {
        SystemCalculatorAgentTool.CalculatorInput input = input("10", "POWER", "2", null);

        AgentToolException exception = assertThrows(AgentToolException.class, () -> tool.calculate(input));

        assertEquals("不支持的计算运算符：POWER", exception.getMessage());
    }

    @Test
    void shouldRejectScaleOutsideAllowedRange() {
        SystemCalculatorAgentTool.CalculatorInput input = input("1", "DIVIDE", "3", 11);

        AgentToolException exception = assertThrows(AgentToolException.class, () -> tool.calculate(input));

        assertEquals("除法小数位数必须在0到10之间", exception.getMessage());
    }

    private static SystemCalculatorAgentTool.CalculatorInput input(String leftOperand, String operator, String rightOperand, Integer scale) {
        SystemCalculatorAgentTool.CalculatorInput input = new SystemCalculatorAgentTool.CalculatorInput();
        input.setLeftOperand(new BigDecimal(leftOperand));
        input.setOperator(operator);
        input.setRightOperand(new BigDecimal(rightOperand));
        input.setScale(scale);
        return input;
    }

    private static AgentToolRequest request(String input) {
        return new AgentToolRequest(input, AgentToolExecutionContext.builder().executionId("test").build(), null);
    }
}

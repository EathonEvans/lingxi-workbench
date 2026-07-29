package com.lingxi.workbench.chat.model.ccontext;

import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.variable.common.enums.BuiltInRuntimeVariable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeVariablesTest {

    @Test
    void shouldDefineAccessorForEveryBuiltInVariable() {
        for (BuiltInRuntimeVariable variable : BuiltInRuntimeVariable.values()) {
            assertEquals(
                    variable,
                    RuntimeVariableAccessors.require(variable).variable());
        }
    }

    @Test
    void shouldReadValuesThroughTypedAccessors() {
        RuntimeVariables variables = new RuntimeVariables();
        variables.put(RuntimeVariableAccessors.USER_ID, 7L);
        variables.put(RuntimeVariableAccessors.USER_QUERY, "如何退款");
        variables.put(
                RuntimeVariableAccessors.SESSION_HISTORY,
                List.of(new ChatHistoryMessage(
                        ChatHistoryMessage.Role.USER,
                        "历史问题")));

        assertEquals(7L, variables.require(RuntimeVariableAccessors.USER_ID));
        assertEquals(
                "如何退款",
                variables.require(RuntimeVariableAccessors.USER_QUERY));
        assertEquals(
                "历史问题",
                variables.require(RuntimeVariableAccessors.SESSION_HISTORY)
                        .getFirst()
                        .content());
        assertTrue(variables.render(
                        RuntimeVariableAccessors.SESSION_HISTORY.variable())
                .orElseThrow()
                .contains("历史问题"));
    }

    @Test
    void shouldRejectDuplicateVariableWrites() {
        RuntimeVariables variables = new RuntimeVariables();
        variables.put(RuntimeVariableAccessors.USER_QUERY, "第一次写入");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> variables.put(
                        RuntimeVariableAccessors.USER_QUERY,
                        "第二次写入"));

        assertTrue(exception.getMessage().contains(
                "运行时变量禁止重复写入：user.query"));
    }

    @Test
    void shouldRejectReadingVariableBeforeProducerRuns() {
        RuntimeVariables variables = new RuntimeVariables();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> variables.require(
                        RuntimeVariableAccessors.SESSION_HISTORY));

        assertTrue(exception.getMessage().contains(
                "运行时变量尚未准备：session.history"));
    }
}

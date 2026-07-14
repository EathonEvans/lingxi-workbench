package com.lingxi.workbench.framework.auth.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StpLogicDefinitionTest {

    @Test
    void shouldUseStableLoginType() {
        TestStpLogic logic = new TestStpLogic("user");

        assertEquals("user", logic.getLoginType());
    }

    @Test
    void shouldRejectBlankLoginType() {
        assertThrows(IllegalArgumentException.class, () -> new TestStpLogic(null));
        assertThrows(IllegalArgumentException.class, () -> new TestStpLogic(" "));
    }

    private static final class TestStpLogic extends StpLogicDefinition {

        private TestStpLogic(String loginType) {
            super(loginType);
        }
    }
}

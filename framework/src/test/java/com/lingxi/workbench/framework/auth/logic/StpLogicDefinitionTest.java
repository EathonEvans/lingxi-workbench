package com.lingxi.workbench.framework.auth.logic;

import cn.dev33.satoken.stp.StpUtil;
import com.lingxi.workbench.framework.auth.StpLogicDefinition;
import com.lingxi.workbench.framework.auth.config.SaTokenExtendConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StpLogicDefinitionTest {

    @Test
    void shouldUseStableLoginType() {
        StpLogicDefinition logic = createStpLogic("user");

        assertEquals("user", logic.getLoginType());
        assertEquals("user", StpUtil.getStpLogic().getLoginType());
    }

    @Test
    void shouldRejectBlankLoginType() {
        assertThrows(IllegalArgumentException.class, () -> createStpLogic(null));
        assertThrows(IllegalArgumentException.class, () -> createStpLogic(" "));
    }

    private static StpLogicDefinition createStpLogic(String loginType) {
        SaTokenExtendConfig config = new SaTokenExtendConfig();
        config.setLoginType(loginType);
        return new StpLogicDefinition(config);
    }
}

package com.lingxi.workbench.admin.auth;

import com.lingxi.workbench.framework.auth.logic.StpLogicDefinition;
import org.springframework.stereotype.Component;

/**
 * Sa-Token account realm used by management APIs.
 */
@Component
public final class AdminStpLogic extends StpLogicDefinition {

    public static final String LOGIN_TYPE = "admin";

    public AdminStpLogic() {
        super(LOGIN_TYPE);
    }
}

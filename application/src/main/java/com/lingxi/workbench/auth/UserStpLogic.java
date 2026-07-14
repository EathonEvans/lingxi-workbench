package com.lingxi.workbench.auth;

import com.lingxi.workbench.framework.auth.logic.StpLogicDefinition;
import org.springframework.stereotype.Component;

/**
 * Sa-Token account realm used by user-facing APIs.
 */
@Component
public final class UserStpLogic extends StpLogicDefinition {

    public static final String LOGIN_TYPE = "user";

    public UserStpLogic() {
        super(LOGIN_TYPE);
    }
}

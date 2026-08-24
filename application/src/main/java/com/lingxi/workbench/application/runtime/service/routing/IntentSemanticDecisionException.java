package com.lingxi.workbench.application.runtime.service.routing;

import com.lingxi.workbench.application.runtime.model.IntentSemanticDecisionResult;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.Getter;

import java.util.Objects;

/** 发布策略要求失败时，保留本次语义决策的安全模型调用事实。 */
@Getter
public class IntentSemanticDecisionException extends BusinessException {

    private final IntentSemanticDecisionResult decisionResult;

    public IntentSemanticDecisionException(IntentSemanticDecisionResult decisionResult, Throwable cause) {
        super(HttpStatusEnum.SERVICE_UNAVAILABLE, "意图语义决策服务暂不可用");
        this.decisionResult = Objects.requireNonNull(decisionResult, "意图语义决策失败事实不能为空");
        if (Objects.nonNull(cause)) {
            initCause(cause);
        }
    }
}

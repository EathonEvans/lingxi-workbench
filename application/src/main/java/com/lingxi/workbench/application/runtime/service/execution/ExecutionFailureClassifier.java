package com.lingxi.workbench.application.runtime.service.execution;

import com.lingxi.workbench.application.runtime.model.ExecutionFailure;
import com.lingxi.workbench.application.runtime.model.ExecutionDeadlineExceededException;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.agentexecute.loop.AgentLoopLimitException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeoutException;

/** 将内部异常归一化为不泄露敏感数据的稳定失败协议。 */
@Component
public class ExecutionFailureClassifier {

    public ExecutionFailure classify(Throwable exception) {
        Throwable current = exception;
        while (Objects.nonNull(current)) {
            if (current instanceof ExecutionDeadlineExceededException) {
                return new ExecutionFailure("EXECUTION_TIME_LIMIT", "本次处理已达到时间上限", true);
            }
            if (current instanceof AgentLoopLimitException limit) {
                return new ExecutionFailure(limit.getErrorCode(), limit.getClientMessage(), true);
            }
            if (current instanceof TimeoutException) {
                return new ExecutionFailure("AI_MODEL_TIMEOUT", "模型响应超时，请重试", true);
            }
            if (current instanceof BusinessException business) {
                String message = Objects.requireNonNullElse(business.getMessage(), "业务规则校验失败");
                boolean timeout = message.contains("超时");
                return new ExecutionFailure(timeout ? "CAPABILITY_TIMEOUT" : "BUSINESS_RULE_REJECTED",
                        message, timeout);
            }
            current = current.getCause();
        }
        return new ExecutionFailure("CAPABILITY_EXECUTION_FAILED", "能力执行失败", true);
    }
}

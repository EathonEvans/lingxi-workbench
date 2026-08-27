package com.lingxi.workbench.application.runtime.service.execution;

import com.lingxi.workbench.agentruntime.model.AgentReconciliationPendingException;
import com.lingxi.workbench.agentruntime.model.AgentRuntimeException;
import com.lingxi.workbench.application.runtime.model.ExecutionFailure;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeoutException;

/** 将内部异常归一化为不泄露敏感数据的稳定失败协议。 */
@Component
public class ExecutionFailureClassifier {

    public ExecutionFailure classify(Throwable exception) {
        Throwable current = exception;
        while (Objects.nonNull(current)) {
            if (current instanceof AgentReconciliationPendingException) {
                return new ExecutionFailure("TOOL_RECONCILIATION_PENDING",
                        "操作结果尚在确认中，请勿重复提交", false);
            }
            if (current instanceof AgentRuntimeException runtime) {
                return runtimeFailure(runtime);
            }
            if (current instanceof TimeoutException) {
                return new ExecutionFailure("AI_MODEL_TIMEOUT", "本次处理等待时间过长，请重试", true);
            }
            if (current instanceof BusinessException business) {
                String message = Objects.requireNonNullElse(business.getMessage(), "业务规则校验失败");
                boolean timeout = message.contains("超时");
                return new ExecutionFailure(timeout ? "CAPABILITY_TIMEOUT" : "BUSINESS_RULE_REJECTED",
                        message, timeout);
            }
            current = current.getCause();
        }
        return new ExecutionFailure("CAPABILITY_EXECUTION_FAILED", "本次处理未完成，请稍后重试", false);
    }

    /** 内部错误码保留给审计，用户只接收可理解、可行动的业务提示。 */
    private static ExecutionFailure runtimeFailure(AgentRuntimeException exception) {
        String message = switch (exception.getCode()) {
            case "AGENT_TOKEN_LIMIT", "AGENT_TOKEN_HARD_LIMIT" ->
                    "本轮可用额度已用完，请缩小问题范围后重试";
            case "AGENT_MODEL_CALL_LIMIT", "AGENT_TOOL_CALL_LIMIT", "CAPABILITY_CALL_LIMIT", "DELEGATION_LIMIT" ->
                    "本轮处理步骤已达到上限，请缩小问题范围后重试";
            case "PLAN_INCOMPLETE" -> "任务未能在本轮完成，请缩小问题范围后重试";
            case "CAPABILITY_INPUT_INVALID", "TASK_NOT_ACTIVE" -> "当前任务信息不完整，请补充后重试";
            case "SUPERVISOR_EMPTY_RESPONSE", "EMPTY_CAPABILITY_RESULT", "CAPABILITY_RESULT_LIMIT" ->
                    "未能生成可用结果，请重试";
            case "SIDE_EFFECT_ALREADY_COMPLETED" -> "该操作已完成，请勿重复提交";
            default -> "本次处理未完成，请稍后重试";
        };
        return new ExecutionFailure(exception.getCode(), message, exception.isRetryable());
    }
}

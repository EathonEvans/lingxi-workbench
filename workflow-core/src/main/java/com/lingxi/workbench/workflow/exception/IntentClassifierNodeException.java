package com.lingxi.workbench.workflow.exception;

import com.lingxi.workbench.workflow.model.IntentClassifierNodeResult;
import lombok.Getter;

import java.util.Objects;

/** 意图分类节点失败时携带不含 Prompt 和槽位值的模型调用事实。 */
@Getter
public class IntentClassifierNodeException extends RuntimeException {

    private final IntentClassifierNodeResult result;

    public IntentClassifierNodeException(String message, IntentClassifierNodeResult result, Throwable cause) {
        super(message, cause);
        this.result = Objects.requireNonNull(result, "意图分类失败结果不能为空");
    }
}

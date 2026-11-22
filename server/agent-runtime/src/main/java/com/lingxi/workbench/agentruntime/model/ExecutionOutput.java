package com.lingxi.workbench.agentruntime.model;

import java.util.Objects;

/** 一次 AgentScope 调用已经对外生成的文本，用于结果返回与 HITL 续跑。 */
public final class ExecutionOutput {

    private final StringBuilder content = new StringBuilder();

    public synchronized void append(String value) {
        if (value != null && !value.isEmpty()) {
            content.append(value);
        }
    }

    public synchronized void restore(String value) {
        content.setLength(0);
        content.append(Objects.requireNonNullElse(value, ""));
    }

    public synchronized String content() {
        return content.toString();
    }
}

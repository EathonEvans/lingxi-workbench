package com.lingxi.workbench.agentruntime.plan;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** AgentScope 运行时请求用户补充信息的安全展示内容。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RuntimeClarification {

    private final String question;
    private final List<String> options;

    public List<String> options() {
        return List.copyOf(options == null ? List.of() : options);
    }
}

package com.lingxi.workbench.admin.agent.model.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Agent 会话记忆窗口策略。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentMemoryPolicy {
    @NotNull(message = "请设置是否启用会话记忆")
    private Boolean enabled;
    @NotNull(message = "请设置会话记忆轮数")
    @Min(value = 0, message = "会话记忆轮数不能小于0")
    @Max(value = 100, message = "会话记忆轮数不能超过100")
    private Integer maxRounds;
    @NotNull(message = "请设置会话记忆Token上限")
    @Min(value = 0, message = "会话记忆Token上限不能小于0")
    @Max(value = 100000, message = "会话记忆Token上限不能超过100000")
    private Integer maxTokens;

    /** 生产环境保守默认值。 */
    public static AgentMemoryPolicy defaults() {
        return AgentMemoryPolicy.builder().enabled(true).maxRounds(10).maxTokens(4000).build();
    }
}

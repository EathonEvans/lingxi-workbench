package com.lingxi.workbench.admin.agent.model.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Agent 单次运行的硬限制。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentRuntimePolicy {
    @NotNull(message = "请设置Agent总超时时间")
    @Min(value = 1, message = "Agent总超时时间不能小于1秒")
    @Max(value = 900, message = "Agent总超时时间不能超过900秒")
    private Integer totalTimeoutSeconds;
    @NotNull(message = "请设置模型调用次数上限")
    @Min(value = 1, message = "模型调用次数上限不能小于1")
    @Max(value = 20, message = "模型调用次数上限不能超过20")
    private Integer maxModelCalls;
    @NotNull(message = "请设置输入Token上限")
    @Min(value = 128, message = "输入Token上限不能小于128")
    @Max(value = 1000000, message = "输入Token上限不能超过1000000")
    private Integer maxInputTokens;
    @NotNull(message = "请设置输出Token上限")
    @Min(value = 16, message = "输出Token上限不能小于16")
    @Max(value = 100000, message = "输出Token上限不能超过100000")
    private Integer maxOutputTokens;

    public static AgentRuntimePolicy defaults() {
        return AgentRuntimePolicy.builder()
                .totalTimeoutSeconds(90)
                .maxModelCalls(3)
                .maxInputTokens(12000)
                .maxOutputTokens(2048)
                .build();
    }
}

package com.lingxi.workbench.infra.ai.agent.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Plan Executor Agent 的结构性规划边界；全局调用、Token 与超时预算归属 Application。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlanExecutorPolicy {

    @Min(value = 1, message = "计划步骤数不能小于1")
    @Max(value = 20, message = "计划步骤数不能大于20")
    private int maxSteps;

    @Min(value = 0, message = "重规划次数不能小于0")
    @Max(value = 3, message = "重规划次数不能大于3")
    private int maxReplans;

    @Min(value = 0, message = "协议修复次数不能小于0")
    @Max(value = 3, message = "协议修复次数不能大于3")
    private int maxProtocolRepairAttempts;

    /** 创建 Plan Executor 草稿时使用的保守默认值。 */
    public static PlanExecutorPolicy defaults() {
        return PlanExecutorPolicy.builder()
                .maxSteps(8)
                .maxReplans(1)
                .maxProtocolRepairAttempts(1)
                .build();
    }

    /** 供控制面和运行时共同执行不依赖 Bean Validation 容器的边界校验。 */
    public boolean isValid() {
        return maxSteps >= 1 && maxSteps <= 20
                && maxReplans >= 0 && maxReplans <= 3
                && maxProtocolRepairAttempts >= 0 && maxProtocolRepairAttempts <= 3;
    }
}

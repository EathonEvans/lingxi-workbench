package com.lingxi.workbench.admin.agent.model.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.lingxi.workbench.infra.ai.tool.common.constants.AgentToolLimitConstants.MAX_RESULT_CHARACTERS;
import static com.lingxi.workbench.infra.ai.tool.common.constants.AgentToolLimitConstants.MIN_RESULT_CHARACTERS;

/** Agent 本地工具调用边界。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentToolPolicy {

    /** 单次工具结果允许回传模型的最大字符数。 */
    @NotNull(message = "工具结果字符上限不能为空")
    @Min(value = MIN_RESULT_CHARACTERS, message = "工具结果字符上限不能小于128")
    @Max(value = MAX_RESULT_CHARACTERS, message = "工具结果字符上限不能大于100000")
    private Integer maxResultCharacters;

    /** 创建新 Agent 时使用的保守默认值。 */
    public static AgentToolPolicy defaults() {
        return AgentToolPolicy.builder()
                .maxResultCharacters(8000)
                .build();
    }
}

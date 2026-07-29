package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import lombok.Data;

/** Prompt 主记录的运行端只读实体，只加载角色和可用状态。 */
@Data
@TableName("lingxi_core.ai_prompt")
public class RuntimePromptEntity {

    private Long id;
    private PromptRole role;
    private String status;
}

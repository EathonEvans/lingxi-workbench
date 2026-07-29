package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeVariableDefinition;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.util.List;

/** PromptVersion 的运行端只读实体。 */
@Data
@TableName(value = "lingxi_core.ai_prompt_version", autoResultMap = true)
public class RuntimePromptVersionEntity {

    private Long id;
    private Long promptId;
    private String promptContent;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private List<PromptRuntimeVariableDefinition> variables;
    private String status;
    /** Prompt 主记录的消息角色，由运行端仓储查询后填充。 */
    @TableField(exist = false)
    private PromptRole role;
}

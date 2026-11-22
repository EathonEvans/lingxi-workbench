package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeVariableDefinition;
import com.lingxi.workbench.framework.persistence.handler.GsonJsonbTypeHandler;
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
    /** 版本号，同一 Prompt 下按版本号递增，运行端按其取最新已发布版本。 */
    private Integer versionNo;
    private String promptContent;
    @TableField(typeHandler = GsonJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private List<PromptRuntimeVariableDefinition> variables;
    private String status;
    /** Prompt 主记录的消息角色，由运行端仓储查询后填充。 */
    @TableField(exist = false)
    private PromptRole role;
}

package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

/** AgentVersion 运行端只读实体。 */
@Data
@TableName(value = "lingxi_core.ai_agent_version", autoResultMap = true)
public class RuntimeAgentVersionEntity {
    private Long id;
    private Long agentId;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private AgentRuntimeDefinition definitionSnapshot;
}

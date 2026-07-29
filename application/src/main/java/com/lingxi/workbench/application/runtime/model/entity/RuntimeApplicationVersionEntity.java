package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

/** ApplicationVersion 运行端只读实体。 */
@Data
@TableName(value = "lingxi_core.ai_application_version", autoResultMap = true)
public class RuntimeApplicationVersionEntity {
    private Long id;
    private Long applicationId;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private ApplicationDefinition definitionSnapshot;
}

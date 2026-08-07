package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

/** 独立 WorkflowVersion 的运行端只读实体。 */
@Data
@TableName(value = "lingxi_core.ai_workflow_version", autoResultMap = true)
public class RuntimeWorkflowVersionEntity {
    private Long id;
    private Long workflowId;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private WorkflowDefinition definitionSnapshot;
    private String snapshotChecksum;
}

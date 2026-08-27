package com.lingxi.workbench.admin.application.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.GsonJsonbTypeHandler;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;

/** AI Application 可编辑草稿，包含 Workflow 定义和 revision 乐观锁。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "lingxi_core.ai_application_draft", autoResultMap = true)
public class AiApplicationDraftEntity extends TimeEntity {
    private Long id;
    private Long applicationId;
    private Long baseVersionId;
    private Integer schemaVersion;
    @TableField(typeHandler = GsonJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private ApplicationDefinition definition;
    private Long revision;
    private String changeNote;
    private Long createdBy;
    private Long updatedBy;
}

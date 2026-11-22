package com.lingxi.workbench.admin.application.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.GsonJsonbTypeHandler;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;

/** AI Application 不可变发布版本，含 Workflow 快照和 SHA-256 校验和。 */
@Data
@TableName(value = "lingxi_core.ai_application_version", autoResultMap = true)
public class AiApplicationVersionEntity {
    private Long id;
    private Long applicationId;
    private Integer versionNo;
    private String versionName;
    private Integer schemaVersion;
    @TableField(typeHandler = GsonJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private ApplicationDefinition definitionSnapshot;
    /** 应用发布时的名称。 */
    private String name;
    /** 应用发布时的头像。 */
    private String avatar;
    private String snapshotChecksum;
    private String changeNote;
    private Long publishedBy;
    private OffsetDateTime publishedAt;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}

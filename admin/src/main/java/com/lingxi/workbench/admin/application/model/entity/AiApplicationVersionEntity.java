package com.lingxi.workbench.admin.application.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
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
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private ApplicationDefinition definitionSnapshot;
    private String snapshotChecksum;
    private String changeNote;
    private Long publishedBy;
    private OffsetDateTime publishedAt;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}

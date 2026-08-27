package com.lingxi.workbench.admin.agent.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.agent.model.config.AgentDefinition;
import com.lingxi.workbench.framework.persistence.handler.GsonJsonbTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;

/**
 * Agent 不可变发布版本数据库实体。
 */
@Data
@TableName(value = "lingxi_core.ai_agent_version", autoResultMap = true)
public class AgentVersionEntity {
    private Long id;
    private Long agentId;
    private Integer versionNo;
    private String versionName;
    private Integer schemaVersion;
    @TableField(typeHandler = GsonJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private AgentDefinition definitionSnapshot;
    private String snapshotChecksum;
    private String changeNote;
    private Long publishedBy;
    private OffsetDateTime publishedAt;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}

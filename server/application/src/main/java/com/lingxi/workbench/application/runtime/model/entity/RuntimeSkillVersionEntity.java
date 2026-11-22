package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.GsonJsonbTypeHandler;
import com.lingxi.workbench.infra.ai.skill.model.SkillVersionSnapshot;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;

/**
 * SkillVersion 运行端只读实体。
 */
@Data
@TableName(value = "lingxi_core.ai_skill_version", autoResultMap = true)
public class RuntimeSkillVersionEntity {

    private Long id;
    private Long skillId;
    private Integer versionNo;
    private Integer schemaVersion;
    @TableField(typeHandler = GsonJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private SkillVersionSnapshot snapshot;
    private String snapshotChecksum;
    private OffsetDateTime revokedAt;
}

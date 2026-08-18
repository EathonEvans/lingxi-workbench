package com.lingxi.workbench.admin.skill.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.skill.model.config.SkillScanReport;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import com.lingxi.workbench.infra.ai.skill.model.SkillVersionSnapshot;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;

/** Skill 不可变发布版本数据库实体。 */
@Data
@TableName(value = "lingxi_core.ai_skill_version", autoResultMap = true)
public class SkillVersionEntity {

    private Long id;
    private Long skillId;
    private Integer versionNo;
    private String versionName;
    private Integer schemaVersion;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private SkillVersionSnapshot snapshot;
    private String snapshotChecksum;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private SkillScanReport scanReport;
    private String changeNote;
    private Long publishedBy;
    private OffsetDateTime publishedAt;
    private Long revokedBy;
    private OffsetDateTime revokedAt;
    private String revokeReason;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}

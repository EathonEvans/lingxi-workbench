package com.lingxi.workbench.admin.skill.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.GsonJsonbTypeHandler;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;

import java.util.List;

/** Skill 可编辑草稿数据库实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "lingxi_core.ai_skill_draft", autoResultMap = true)
public class SkillDraftEntity extends TimeEntity {

    private Long id;
    private Long skillId;
    private Long baseVersionId;
    private Integer schemaVersion;
    private String instructions;
    private SkillActivationMode activationMode;
    @TableField(typeHandler = GsonJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private List<String> requestedToolCodes;
    private Long revision;
    private String changeNote;
    private Long createdBy;
    private Long updatedBy;
}

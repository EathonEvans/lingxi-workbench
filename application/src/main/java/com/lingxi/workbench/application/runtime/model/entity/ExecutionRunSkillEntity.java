package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.GsonJsonbTypeHandler;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationReason;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;
import java.util.List;

/** 一次 Agent 调用实际激活的 SkillVersion 运行事实。 */
@Data
@TableName(value = "lingxi_core.ai_execution_run_skill", autoResultMap = true)
public class ExecutionRunSkillEntity {

    private Long id;
    private String runId;
    private Long eventSequenceNo;
    private Long agentVersionId;
    private Long skillId;
    private Long skillVersionId;
    private String skillCode;
    private String skillChecksum;
    private SkillActivationReason activationReason;
    @TableField(typeHandler = GsonJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private List<String> effectiveToolCodes;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}

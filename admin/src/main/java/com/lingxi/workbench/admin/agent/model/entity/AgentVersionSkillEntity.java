package com.lingxi.workbench.admin.agent.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;
import java.util.List;

/** AgentVersion 与锁定 SkillVersion 的不可变关系索引。 */
@Data
@TableName(value = "lingxi_core.ai_agent_version_skill", autoResultMap = true)
public class AgentVersionSkillEntity {

    private Long id;
    private Long agentVersionId;
    private Long skillId;
    private Long skillVersionId;
    private String skillCode;
    private String skillChecksum;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private List<String> effectiveToolCodes;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private List<String> deniedToolCodes;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}

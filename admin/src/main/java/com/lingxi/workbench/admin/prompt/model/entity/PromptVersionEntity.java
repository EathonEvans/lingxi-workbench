package com.lingxi.workbench.admin.prompt.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVersionStatus;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import com.lingxi.workbench.framework.persistence.model.AuditTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;
import java.util.List;

/** Prompt版本数据库实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "lingxi_core.ai_prompt_version", autoResultMap = true)
public class PromptVersionEntity extends AuditTimeEntity {
    /** 版本主键。 */
    private Long id;
    /** 所属Prompt主键。 */
    private Long promptId;
    /** 版本号，同一Prompt下递增且唯一。 */
    private Integer versionNo;
    /** Prompt正文内容。 */
    private String promptContent;
    /** 变量声明列表，以JSONB形式存储。 */
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private List<PromptVariableDefinition> variables;
    /** 版本状态：DRAFT / PUBLISHED。 */
    private PromptVersionStatus status;
    /** 版本变更说明。 */
    private String changeNote;
    /** 发布时间，草稿状态时为空。 */
    private OffsetDateTime publishedAt;
    /** 创建人用户主键。 */
    private Long createdBy;
    /** 更新人用户主键。 */
    private Long updatedBy;
}

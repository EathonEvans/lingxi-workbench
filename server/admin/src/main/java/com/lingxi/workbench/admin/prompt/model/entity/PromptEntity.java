package com.lingxi.workbench.admin.prompt.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Prompt主记录数据库实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_prompt")
public class PromptEntity extends TimeEntity {
    /** Prompt主键。 */
    private Long id;
    /** Prompt名称。 */
    private String name;
    /** Prompt编码，全局唯一。 */
    private String code;
    /** Prompt角色。 */
    private PromptRole role;
    /** Prompt描述说明。 */
    private String description;
    /** 启用状态：ACTIVE / DISABLED。 */
    private EnableStatus status;
    /** 当前已发布版本主键。 */
    private Long publishedVersionId;
    /** 创建人用户主键。 */
    private Long createdBy;
    /** 更新人用户主键。 */
    private Long updatedBy;
}

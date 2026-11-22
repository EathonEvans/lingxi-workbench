package com.lingxi.workbench.admin.application.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** AI Application 稳定身份。
 *  <p>核心字段：id, name, code, description, avatar, status, publishedVersionId。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_application")
public class AiApplicationEntity extends TimeEntity {
    private Long id;
    private String name;
    private String code;
    private String description;
    /** 当前应用头像，发布时冻结至 ApplicationVersion。 */
    private String avatar;
    private Long domainId;
    private EnableStatus status;
    private Long publishedVersionId;
    private Long createdBy;
    private Long updatedBy;
}

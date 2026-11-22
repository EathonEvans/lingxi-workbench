package com.lingxi.workbench.admin.intent.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 意图树稳定身份。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_intent_tree")
public class IntentTreeEntity extends TimeEntity {

    private Long id;
    private String name;
    private String code;
    private String description;
    private Long domainId;
    private EnableStatus status;
    private Long publishedVersionId;
    private Long createdBy;
    private Long updatedBy;
}

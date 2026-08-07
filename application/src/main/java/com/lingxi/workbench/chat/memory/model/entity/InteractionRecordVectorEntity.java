package com.lingxi.workbench.chat.memory.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** 交互记录向量索引实体，位于 lingxi_vector schema。 */
@Data
@TableName("lingxi_vector.interaction_record_vector")
public class InteractionRecordVectorEntity {
    private Long id;
    private Long recordId;
    private Long embeddingModelInstanceId;
    private Integer embeddingDimension;
    private float[] embedding;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}

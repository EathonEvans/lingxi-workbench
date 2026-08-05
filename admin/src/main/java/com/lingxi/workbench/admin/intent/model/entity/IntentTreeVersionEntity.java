package com.lingxi.workbench.admin.intent.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;

/** 意图树不可变发布版本。 */
@Data
@TableName(value = "lingxi_core.ai_intent_tree_version", autoResultMap = true)
public class IntentTreeVersionEntity {

    private Long id;
    private Long intentTreeId;
    private Integer versionNo;
    private String versionName;
    private Integer schemaVersion;
    private Long embeddingModelInstanceId;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private IntentTreeDefinition definitionSnapshot;
    private String snapshotChecksum;
    private String changeNote;
    private Long publishedBy;
    private OffsetDateTime publishedAt;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}

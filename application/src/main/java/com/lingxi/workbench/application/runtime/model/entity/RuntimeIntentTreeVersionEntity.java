package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

/** 运行端只读的意图树不可变发布版本。 */
@Data
@TableName(value = "lingxi_core.ai_intent_tree_version", autoResultMap = true)
public class RuntimeIntentTreeVersionEntity {
    private Long id;
    private Long intentTreeId;
    private Long embeddingModelInstanceId;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private IntentTreeDefinition definitionSnapshot;
}

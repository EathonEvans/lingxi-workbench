package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

/**
 * 运行端只读的意图树不可变发布版本实体，映射到 {@code lingxi_core.ai_intent_tree_version} 表。
 *
 * <p>ApplicationVersion 发布时锁定 {@code intentTreeVersionId}，
 * 运行时通过该 ID 读取对应的不可变版本快照。</p>
 *
 * <p>该实体仅包含运行时需要的字段（ID、意图树 ID、Embedding 模型 ID、定义快照），
 * 不包含发布者、发布时间、版本说明等管理端字段，减少序列化开销。</p>
 *
 * <p>{@code definitionSnapshot} 为 JSONB 字段，通过 Fastjson2JsonbTypeHandler
 * 自动反序列化为 {@link IntentTreeDefinition} 对象。</p>
 */
@Data
@TableName(value = "lingxi_core.ai_intent_tree_version", autoResultMap = true)
public class RuntimeIntentTreeVersionEntity {

    /** 版本主键 */
    private Long id;

    /** 意图树身份主键（逻辑外键 → ai_intent_tree.id） */
    private Long intentTreeId;

    /** 发布时使用的 Embedding 模型实例主键（逻辑外键 → ai_model_instance.id） */
    private Long embeddingModelInstanceId;

    /** 不可变意图树定义快照（JSONB → IntentTreeDefinition） */
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private IntentTreeDefinition definitionSnapshot;
}
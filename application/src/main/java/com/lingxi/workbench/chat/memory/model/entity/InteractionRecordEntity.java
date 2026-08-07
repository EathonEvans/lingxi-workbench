package com.lingxi.workbench.chat.memory.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;
import java.util.Map;

/** 交互记录数据库实体。summary 为富文本叙述，entities 为结构化实体数据。 */
@Data
@TableName(value = "lingxi_core.ai_interaction_record", autoResultMap = true)
public class InteractionRecordEntity {
    private Long id;
    private Long userId;
    private Long conversationId;
    private Long assistantMessageId;
    private String summary;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, Object> entities;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}

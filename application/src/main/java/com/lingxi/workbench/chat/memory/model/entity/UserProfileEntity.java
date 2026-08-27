package com.lingxi.workbench.chat.memory.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.GsonJsonbTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 用户画像数据库实体。画像以 JSONB 声明式格式存储用户偏好和特征。
 */
@Data
@TableName(value = "lingxi_core.ai_user_profile", autoResultMap = true)
public class UserProfileEntity {
    private Long id;
    private Long userId;
    @TableField(typeHandler = GsonJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, Object> profile;
    private Integer version;
    private Long sourceMessageId;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

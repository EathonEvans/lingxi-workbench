package com.lingxi.workbench.application.runtime.model.entity;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

/**
 * Tool 运行端只读定义。
 *
 * <p>Application 模块只读取控制面发布的契约，不复用 Admin 实体，
 * 从模块边界上避免运行端依赖管理端实现。</p>
 */
@Data
@TableName(value = "lingxi_core.ai_tool", autoResultMap = true)
public class RuntimeToolEntity {

    private Long id;

    private String name;

    private String code;

    private String description;

    private String sourceType;

    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JSONObject parameterSchema;

    private String status;
}

package com.lingxi.workbench.application.runtime.model.entity;

import com.lingxi.workbench.framework.json.JSONObject;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.GsonJsonbTypeHandler;
import com.lingxi.workbench.application.runtime.common.enums.RuntimeToolSourceType;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;
import com.lingxi.workbench.infra.ai.tool.common.enums.ToolRiskLevel;

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

    private RuntimeToolSourceType sourceType;

    private Long mcpServerId;

    private String remoteToolName;

    /** MCP 对账查询 Tool 名称；为空表示该 Tool 不支持自动查询外部效果。 */
    private String reconciliationQueryToolName;

    @TableField(typeHandler = GsonJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JSONObject parameterSchema;

    private ToolRiskLevel riskLevel;

    private Boolean approvalRequired;

    private Integer approvalTtlSeconds;

    private String resourceScope;

    private String status;
}

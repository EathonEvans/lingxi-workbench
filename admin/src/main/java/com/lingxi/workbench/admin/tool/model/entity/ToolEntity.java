package com.lingxi.workbench.admin.tool.model.entity;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.tool.common.enums.ToolSourceType;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import com.lingxi.workbench.infra.ai.tool.common.enums.ToolRiskLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;

/**
 * Tool 控制面稳定定义。
 *
 * <p>该实体只描述模型能够看到的工具契约，不保存 Java 类名、Bean 或可执行脚本。
 * 运行端通过稳定的 {@link #code} 查找已部署执行器，从而避免控制面依赖业务代码。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "lingxi_core.ai_tool", autoResultMap = true)
public class ToolEntity extends TimeEntity {

    /** Tool 主键。 */
    private Long id;

    /** 管理端展示名称。 */
    private String name;

    /** Agent 配置和运行时执行器共同使用的稳定编码。 */
    private String code;

    /** 提供给模型判断调用时机的清晰说明。 */
    private String description;

    /** Tool 执行来源。 */
    private ToolSourceType sourceType;

    /** MCP 来源工具绑定的 Server 主体主键；本地函数为空。 */
    private Long mcpServerId;

    /** MCP Server 暴露的原始工具名称；本地函数为空。 */
    private String remoteToolName;

    /** MCP Server 用于按幂等键查询原副作用结果的只读 Tool 名称。 */
    private String reconciliationQueryToolName;

    /** 符合 OpenAI Function Calling 规范的参数 JSON Schema。 */
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JSONObject parameterSchema;

    /** 工具风险等级，与是否需要人工批准分别配置。 */
    private ToolRiskLevel riskLevel;

    /** true 时实际调用参数必须经过用户批准。 */
    private Boolean approvalRequired;

    /** 审批有效期，单位秒。 */
    private Integer approvalTtlSeconds;

    /** 审批卡片展示的资源作用域。 */
    private String resourceScope;

    /** ACTIVE 时允许 Agent 新增绑定，DISABLED 时禁止发布。 */
    private EnableStatus status;

    /** 创建人用户主键。 */
    private Long createdBy;

    /** 更新人用户主键。 */
    private Long updatedBy;
}

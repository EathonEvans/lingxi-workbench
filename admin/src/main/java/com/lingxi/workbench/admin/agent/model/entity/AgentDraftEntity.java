package com.lingxi.workbench.admin.agent.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.agent.model.config.AgentToolPolicy;
import com.lingxi.workbench.admin.agent.model.config.GenerationPolicy;
import com.lingxi.workbench.admin.agent.model.config.RetrievalPolicy;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import com.lingxi.workbench.infra.ai.agent.model.PlanPolicy;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;

import java.util.List;

/** Agent 可编辑草稿数据库实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "lingxi_core.ai_agent_draft", autoResultMap = true)
public class AgentDraftEntity extends TimeEntity {
    private Long id;
    private Long agentId;
    private Long baseVersionId;
    private Integer schemaVersion;
    private Long chatModelInstanceId;
    /** 回答阶段 SYSTEM 角色 PromptVersion 主键。 */
    private Long systemPromptVersionId;
    /** 回答阶段 USER 角色 PromptVersion 主键。 */
    private Long userPromptVersionId;
    /** 当前草稿允许暴露给模型的本地工具编码。 */
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private List<String> toolCodes;
    /** 当前草稿选中的平台预设 Skill 主键。 */
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private List<Long> skillIds;
    /** 工具调用次数和结果大小边界。 */
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private AgentToolPolicy toolPolicy;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private RetrievalPolicy retrievalPolicy;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private GenerationPolicy generationPolicy;
    /** 主Agent专属规划边界，领域专家必须为空。 */
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private PlanPolicy planPolicy;
    private Long revision;
    private String changeNote;
    private Long createdBy;
    private Long updatedBy;
}

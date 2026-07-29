package com.lingxi.workbench.admin.agent.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.agent.model.config.AgentMemoryPolicy;
import com.lingxi.workbench.admin.agent.model.config.AgentRuntimePolicy;
import com.lingxi.workbench.admin.agent.model.config.GenerationPolicy;
import com.lingxi.workbench.admin.agent.model.config.QueryRewritePolicy;
import com.lingxi.workbench.admin.agent.model.config.RetrievalPolicy;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;

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
    private Long rewriteModelInstanceId;
    /** 回答阶段 SYSTEM 角色 PromptVersion 主键。 */
    private Long systemPromptVersionId;
    /** 回答阶段 USER 角色 PromptVersion 主键。 */
    private Long userPromptVersionId;
    private Long rewritePromptVersionId;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private AgentMemoryPolicy memoryPolicy;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private QueryRewritePolicy rewritePolicy;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private RetrievalPolicy retrievalPolicy;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private GenerationPolicy generationPolicy;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private AgentRuntimePolicy runtimePolicy;
    private Long revision;
    private String changeNote;
    private Long createdBy;
    private Long updatedBy;
}

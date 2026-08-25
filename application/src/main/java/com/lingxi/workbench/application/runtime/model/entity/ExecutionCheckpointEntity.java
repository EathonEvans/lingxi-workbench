package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionCheckpointStatus;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionCheckpointType;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpoint;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;

/**
 * 人工等待期间用于恢复同一 Run 的不可变检查点。
 */
@Data
@TableName(value = "lingxi_core.ai_execution_checkpoint", autoResultMap = true)
public class ExecutionCheckpointEntity {

    /**
     * 主键
     */
    private Long id;
    /**
     * 所属执行 Run 标识
     */
    private String runId;
    /**
     * 触发人工交互的 Assistant 消息主键
     */
    private Long assistantMessageId;
    /**
     * 检查点类型：工具审批 / 计划审阅
     */
    private ExecutionCheckpointType checkpointType;
    /**
     * 生命周期状态：活跃 / 已消费 / 已取代 / 已过期
     */
    private ExecutionCheckpointStatus status;
    /**
     * 检查点协议版本，与 AgentScopeCheckpoint.CURRENT_VERSION 对应
     */
    private Integer checkpointVersion;
    /**
     * AgentScope 可恢复状态与 Lingxi 审批事实（JSONB）
     */
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private AgentScopeCheckpoint payload;
    /**
     * 快照指纹校验和，恢复时校验防篡改
     */
    private String checksum;
    /**
     * 过期时间，人工等待超时上限
     */
    private OffsetDateTime expiresAt;
    /**
     * 被消费（恢复执行）时间，未消费时为空
     */
    private OffsetDateTime consumedAt;
    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;
}

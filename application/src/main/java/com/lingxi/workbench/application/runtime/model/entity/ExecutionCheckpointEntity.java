package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionCheckpointStatus;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionCheckpointType;
import com.lingxi.workbench.application.runtime.model.ExecutionCheckpointPayload;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;

/** 人工等待期间用于恢复同一 Run 的不可变检查点。 */
@Data
@TableName(value = "lingxi_core.ai_execution_checkpoint", autoResultMap = true)
public class ExecutionCheckpointEntity {

    private Long id;
    private String runId;
    private Long assistantMessageId;
    private ExecutionCheckpointType checkpointType;
    private ExecutionCheckpointStatus status;
    private Integer checkpointVersion;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private ExecutionCheckpointPayload payload;
    private String checksum;
    private OffsetDateTime expiresAt;
    private OffsetDateTime consumedAt;
    private OffsetDateTime createdAt;
}

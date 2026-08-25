package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import io.agentscope.core.state.Task;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;
import java.util.List;

/** AgentScope Task 清单的追加式审计快照。 */
@Data
@TableName(value = "lingxi_core.ai_execution_task_snapshot", autoResultMap = true)
public class ExecutionTaskSnapshotEntity {

    private Long id;
    private String runId;
    private Integer revision;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private List<Task> taskSnapshot;
    private String snapshotChecksum;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}

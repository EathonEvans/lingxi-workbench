package com.lingxi.workbench.admin.appmonitor.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

/** 会话异步链路运维总览；数据源不可用时必须显式返回状态。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationOpsOverviewVO {
    private boolean sourceAvailable;
    private String sourceMessage;
    private boolean mqAvailable;
    private String mqMessage;
    private OffsetDateTime checkedAt;
    private long staleGenerating;
    private long pendingMemoryReconcile;
    private long dlqTotal;
    private Map<String, Long> dlqByGroup;
}

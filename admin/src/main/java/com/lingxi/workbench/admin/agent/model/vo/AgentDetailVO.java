package com.lingxi.workbench.admin.agent.model.vo;

import com.lingxi.workbench.infra.ai.agent.common.enums.AgentType;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** Agent 编辑详情响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentDetailVO {
    private String id;
    private String name;
    private String code;
    private String description;
    private AgentType agentType;
    private EnableStatus status;
    private String publishedVersionId;
    private Integer publishedVersionNo;
    private AgentDraftVO draft;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

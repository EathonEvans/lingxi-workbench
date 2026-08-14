package com.lingxi.workbench.admin.agent.model.vo;

import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** Agent 列表响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentVO {
    private String id;
    private String name;
    private String code;
    private String description;
    private AgentRole role;
    private String domainId;
    private EnableStatus status;
    private String publishedVersionId;
    private Integer publishedVersionNo;
    private boolean publishedVersionReady;
    private Long draftRevision;
    private OffsetDateTime updatedAt;
}

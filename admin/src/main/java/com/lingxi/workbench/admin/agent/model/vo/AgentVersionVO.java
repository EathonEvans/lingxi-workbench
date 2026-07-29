package com.lingxi.workbench.admin.agent.model.vo;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** Agent 发布版本摘要响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentVersionVO {
    private String id;
    private Integer versionNo;
    private String versionName;
    private Integer schemaVersion;
    private String snapshotChecksum;
    private String changeNote;
    private String publishedBy;
    private OffsetDateTime publishedAt;
}

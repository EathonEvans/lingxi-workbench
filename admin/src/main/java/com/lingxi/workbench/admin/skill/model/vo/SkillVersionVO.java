package com.lingxi.workbench.admin.skill.model.vo;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** Skill 历史版本摘要。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SkillVersionVO {

    private String id;
    private Integer versionNo;
    private String versionName;
    private Integer schemaVersion;
    private String snapshotChecksum;
    private String changeNote;
    private String publishedBy;
    private OffsetDateTime publishedAt;
    private boolean revoked;
    private String revokeReason;
}

package com.lingxi.workbench.admin.skill.model.vo;

import com.lingxi.workbench.admin.skill.model.config.SkillScanReport;
import com.lingxi.workbench.infra.ai.skill.model.SkillVersionSnapshot;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** Skill 历史版本完整快照。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SkillVersionDetailVO {

    private String id;
    private String skillId;
    private Integer versionNo;
    private String versionName;
    private Integer schemaVersion;
    private SkillVersionSnapshot snapshot;
    private String snapshotChecksum;
    private SkillScanReport scanReport;
    private String changeNote;
    private String publishedBy;
    private OffsetDateTime publishedAt;
    private boolean revoked;
    private String revokedBy;
    private OffsetDateTime revokedAt;
    private String revokeReason;
}

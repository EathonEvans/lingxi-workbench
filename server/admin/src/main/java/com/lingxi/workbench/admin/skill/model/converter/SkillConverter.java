package com.lingxi.workbench.admin.skill.model.converter;

import com.lingxi.workbench.admin.skill.model.entity.SkillDraftEntity;
import com.lingxi.workbench.admin.skill.model.entity.SkillEntity;
import com.lingxi.workbench.admin.skill.model.entity.SkillVersionEntity;
import com.lingxi.workbench.admin.skill.model.vo.SkillDetailVO;
import com.lingxi.workbench.admin.skill.model.vo.SkillDraftVO;
import com.lingxi.workbench.admin.skill.model.vo.SkillVO;
import com.lingxi.workbench.admin.skill.model.vo.SkillVersionDetailVO;
import com.lingxi.workbench.admin.skill.model.vo.SkillVersionVO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** Skill 实体、草稿和不可变快照之间的显式转换器。 */
@Component
public class SkillConverter {

    public SkillVO toListVO(SkillEntity skill, SkillVersionEntity publishedVersion, SkillDraftEntity draft, boolean versionReady) {
        return SkillVO.builder()
                .id(stringId(skill.getId()))
                .name(skill.getName())
                .code(skill.getCode())
                .description(skill.getDescription())
                .sourceType(skill.getSourceType())
                .visibility(skill.getVisibility())
                .status(skill.getStatus())
                .securityStatus(skill.getSecurityStatus())
                .publishedVersionId(stringId(skill.getPublishedVersionId()))
                .publishedVersionNo(Objects.isNull(publishedVersion) ? null : publishedVersion.getVersionNo())
                .publishedVersionReady(versionReady)
                .draftRevision(Objects.isNull(draft) ? null : draft.getRevision())
                .updatedAt(skill.getUpdatedAt())
                .build();
    }

    public SkillDetailVO toDetailVO(SkillEntity skill, SkillVersionEntity publishedVersion, SkillDraftEntity draft, boolean versionReady) {
        return SkillDetailVO.builder()
                .id(stringId(skill.getId()))
                .name(skill.getName())
                .code(skill.getCode())
                .description(skill.getDescription())
                .sourceType(skill.getSourceType())
                .visibility(skill.getVisibility())
                .status(skill.getStatus())
                .securityStatus(skill.getSecurityStatus())
                .publishedVersionId(stringId(skill.getPublishedVersionId()))
                .publishedVersionNo(Objects.isNull(publishedVersion) ? null : publishedVersion.getVersionNo())
                .publishedVersionReady(versionReady)
                .draft(toDraftVO(draft))
                .createdAt(skill.getCreatedAt())
                .updatedAt(skill.getUpdatedAt())
                .build();
    }

    public SkillVersionVO toVersionVO(SkillVersionEntity version) {
        return SkillVersionVO.builder()
                .id(stringId(version.getId()))
                .versionNo(version.getVersionNo())
                .versionName(version.getVersionName())
                .schemaVersion(version.getSchemaVersion())
                .snapshotChecksum(version.getSnapshotChecksum())
                .changeNote(version.getChangeNote())
                .publishedBy(stringId(version.getPublishedBy()))
                .publishedAt(version.getPublishedAt())
                .revoked(Objects.nonNull(version.getRevokedAt()))
                .revokeReason(version.getRevokeReason())
                .build();
    }

    public SkillVersionDetailVO toVersionDetailVO(SkillVersionEntity version) {
        return SkillVersionDetailVO.builder()
                .id(stringId(version.getId()))
                .skillId(stringId(version.getSkillId()))
                .versionNo(version.getVersionNo())
                .versionName(version.getVersionName())
                .schemaVersion(version.getSchemaVersion())
                .snapshot(version.getSnapshot())
                .snapshotChecksum(version.getSnapshotChecksum())
                .scanReport(version.getScanReport())
                .changeNote(version.getChangeNote())
                .publishedBy(stringId(version.getPublishedBy()))
                .publishedAt(version.getPublishedAt())
                .revoked(Objects.nonNull(version.getRevokedAt()))
                .revokedBy(stringId(version.getRevokedBy()))
                .revokedAt(version.getRevokedAt())
                .revokeReason(version.getRevokeReason())
                .build();
    }

    private static SkillDraftVO toDraftVO(SkillDraftEntity draft) {
        return SkillDraftVO.builder()
                .id(stringId(draft.getId()))
                .baseVersionId(stringId(draft.getBaseVersionId()))
                .schemaVersion(draft.getSchemaVersion())
                .instructions(draft.getInstructions())
                .activationMode(draft.getActivationMode())
                .requestedToolCodes(Objects.requireNonNullElse(draft.getRequestedToolCodes(), List.of()))
                .revision(draft.getRevision())
                .changeNote(draft.getChangeNote())
                .build();
    }

    private static String stringId(Long id) {
        return Objects.isNull(id) ? null : String.valueOf(id);
    }
}

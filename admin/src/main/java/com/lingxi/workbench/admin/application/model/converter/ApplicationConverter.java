package com.lingxi.workbench.admin.application.model.converter;

import com.lingxi.workbench.admin.application.model.entity.AiApplicationDraftEntity;
import com.lingxi.workbench.admin.application.model.entity.AiApplicationEntity;
import com.lingxi.workbench.admin.application.model.entity.AiApplicationVersionEntity;
import com.lingxi.workbench.admin.application.model.vo.ApplicationDetailVO;
import com.lingxi.workbench.admin.application.model.vo.ApplicationDraftVO;
import com.lingxi.workbench.admin.application.model.vo.ApplicationVO;
import com.lingxi.workbench.admin.application.model.vo.ApplicationVersionDetailVO;
import com.lingxi.workbench.admin.application.model.vo.ApplicationVersionVO;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;

/** Application 实体、快照和管理端响应之间的显式转换器。 */
@Component
public class ApplicationConverter {

    /** 组装 Application 列表 VO，含生效版本号与草稿版本号。 */
    public ApplicationVO toListVO(
            AiApplicationEntity application,
            AiApplicationVersionEntity publishedVersion,
            AiApplicationDraftEntity draft) {
        return ApplicationVO.builder()
                .id(stringId(application.getId()))
                .name(application.getName())
                .code(application.getCode())
                .description(application.getDescription())
                .domainId(stringId(application.getDomainId()))
                .status(application.getStatus())
                .publishedVersionId(stringId(application.getPublishedVersionId()))
                .publishedVersionNo(publishedVersion == null ? null : publishedVersion.getVersionNo())
                .publishedVersionReady(isPublishedVersionReady(publishedVersion))
                .draftRevision(draft == null ? null : draft.getRevision())
                .updatedAt(application.getUpdatedAt())
                .build();
    }

    /** 组装 Application 详情 VO，含主记录、生效版本和草稿。 */
    public ApplicationDetailVO toDetailVO(
            AiApplicationEntity application,
            AiApplicationVersionEntity publishedVersion,
            AiApplicationDraftEntity draft) {
        return ApplicationDetailVO.builder()
                .id(stringId(application.getId()))
                .name(application.getName())
                .code(application.getCode())
                .description(application.getDescription())
                .domainId(stringId(application.getDomainId()))
                .status(application.getStatus())
                .publishedVersionId(stringId(application.getPublishedVersionId()))
                .publishedVersionNo(publishedVersion == null ? null : publishedVersion.getVersionNo())
                .publishedVersionReady(isPublishedVersionReady(publishedVersion))
                .draft(toDraftVO(draft))
                .createdAt(application.getCreatedAt())
                .updatedAt(application.getUpdatedAt())
                .build();
    }

    /** 组装草稿 VO。 */
    public ApplicationDraftVO toDraftVO(AiApplicationDraftEntity draft) {
        return ApplicationDraftVO.builder()
                .id(stringId(draft.getId()))
                .baseVersionId(stringId(draft.getBaseVersionId()))
                .schemaVersion(draft.getSchemaVersion())
                .definition(draft.getDefinition())
                .revision(draft.getRevision())
                .changeNote(draft.getChangeNote())
                .build();
    }

    /** 组装版本摘要 VO。 */
    public ApplicationVersionVO toVersionVO(AiApplicationVersionEntity version) {
        return ApplicationVersionVO.builder()
                .id(stringId(version.getId()))
                .versionNo(version.getVersionNo())
                .versionName(version.getVersionName())
                .schemaVersion(version.getSchemaVersion())
                .snapshotChecksum(version.getSnapshotChecksum())
                .changeNote(version.getChangeNote())
                .publishedBy(stringId(version.getPublishedBy()))
                .publishedAt(version.getPublishedAt())
                .build();
    }

    /** 组装版本详情 VO，含完整 Workflow 定义快照。 */
    public ApplicationVersionDetailVO toVersionDetailVO(AiApplicationVersionEntity version) {
        return ApplicationVersionDetailVO.builder()
                .id(stringId(version.getId()))
                .applicationId(stringId(version.getApplicationId()))
                .versionNo(version.getVersionNo())
                .versionName(version.getVersionName())
                .schemaVersion(version.getSchemaVersion())
                .definition(version.getDefinitionSnapshot())
                .snapshotChecksum(version.getSnapshotChecksum())
                .changeNote(version.getChangeNote())
                .publishedBy(stringId(version.getPublishedBy()))
                .publishedAt(version.getPublishedAt())
                .build();
    }

    private static String stringId(Long id) {
        return Objects.toString(id, null);
    }

    /** 控制面显式标记旧协议正式版本，避免运营误以为当前 Application 可运行。 */
    private static boolean isPublishedVersionReady(AiApplicationVersionEntity version) {
        return Objects.nonNull(version)
                && Objects.equals(version.getSchemaVersion(), APPLICATION_SCHEMA_VERSION)
                && Objects.nonNull(version.getDefinitionSnapshot())
                && version.getDefinitionSnapshot().schemaVersion() == APPLICATION_SCHEMA_VERSION;
    }
}

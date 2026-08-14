package com.lingxi.workbench.admin.intent.model.converter;

import com.lingxi.workbench.admin.intent.model.entity.IntentTreeDraftEntity;
import com.lingxi.workbench.admin.intent.model.entity.IntentTreeEntity;
import com.lingxi.workbench.admin.intent.model.entity.IntentTreeVersionEntity;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeDetailVO;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeDraftVO;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeVO;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeVersionDetailVO;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeVersionVO;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 意图树实体、草稿和发布快照响应转换。 */
@Component
public class IntentTreeConverter {

    public IntentTreeVO toListVO(IntentTreeEntity tree, IntentTreeVersionEntity publishedVersion, IntentTreeDraftEntity draft) {
        return IntentTreeVO.builder()
                .id(stringId(tree.getId()))
                .name(tree.getName())
                .code(tree.getCode())
                .description(tree.getDescription())
                .domainId(stringId(tree.getDomainId()))
                .status(tree.getStatus())
                .publishedVersionId(stringId(tree.getPublishedVersionId()))
                .publishedVersionNo(Objects.isNull(publishedVersion) ? null : publishedVersion.getVersionNo())
                .draftRevision(Objects.isNull(draft) ? null : draft.getRevision())
                .updatedAt(tree.getUpdatedAt())
                .build();
    }

    public IntentTreeDetailVO toDetailVO(IntentTreeEntity tree, IntentTreeVersionEntity publishedVersion, IntentTreeDraftEntity draft) {
        return IntentTreeDetailVO.builder()
                .id(stringId(tree.getId()))
                .name(tree.getName())
                .code(tree.getCode())
                .description(tree.getDescription())
                .domainId(stringId(tree.getDomainId()))
                .status(tree.getStatus())
                .publishedVersionId(stringId(tree.getPublishedVersionId()))
                .publishedVersionNo(Objects.isNull(publishedVersion) ? null : publishedVersion.getVersionNo())
                .draft(toDraftVO(draft))
                .createdAt(tree.getCreatedAt())
                .updatedAt(tree.getUpdatedAt())
                .build();
    }

    public IntentTreeDraftVO toDraftVO(IntentTreeDraftEntity draft) {
        return IntentTreeDraftVO.builder()
                .id(stringId(draft.getId()))
                .baseVersionId(stringId(draft.getBaseVersionId()))
                .schemaVersion(draft.getSchemaVersion())
                .embeddingModelInstanceId(stringId(draft.getEmbeddingModelInstanceId()))
                .definition(draft.getDefinition())
                .revision(draft.getRevision())
                .changeNote(draft.getChangeNote())
                .build();
    }

    public IntentTreeVersionVO toVersionVO(IntentTreeVersionEntity version) {
        return IntentTreeVersionVO.builder()
                .id(stringId(version.getId()))
                .versionNo(version.getVersionNo())
                .versionName(version.getVersionName())
                .schemaVersion(version.getSchemaVersion())
                .embeddingModelInstanceId(stringId(version.getEmbeddingModelInstanceId()))
                .snapshotChecksum(version.getSnapshotChecksum())
                .changeNote(version.getChangeNote())
                .publishedBy(stringId(version.getPublishedBy()))
                .publishedAt(version.getPublishedAt())
                .build();
    }

    public IntentTreeVersionDetailVO toVersionDetailVO(IntentTreeVersionEntity version) {
        return IntentTreeVersionDetailVO.builder()
                .id(stringId(version.getId()))
                .intentTreeId(stringId(version.getIntentTreeId()))
                .versionNo(version.getVersionNo())
                .versionName(version.getVersionName())
                .schemaVersion(version.getSchemaVersion())
                .embeddingModelInstanceId(stringId(version.getEmbeddingModelInstanceId()))
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
}

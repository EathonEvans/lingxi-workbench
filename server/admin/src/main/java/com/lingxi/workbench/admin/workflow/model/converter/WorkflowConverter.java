package com.lingxi.workbench.admin.workflow.model.converter;

import com.lingxi.workbench.admin.workflow.model.entity.WorkflowDraftEntity;
import com.lingxi.workbench.admin.workflow.model.entity.WorkflowEntity;
import com.lingxi.workbench.admin.workflow.model.entity.WorkflowVersionEntity;
import com.lingxi.workbench.admin.workflow.model.vo.WorkflowDetailVO;
import com.lingxi.workbench.admin.workflow.model.vo.WorkflowDraftVO;
import com.lingxi.workbench.admin.workflow.model.vo.WorkflowVO;
import com.lingxi.workbench.admin.workflow.model.vo.WorkflowVersionVO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** Workflow 实体和 VO 之间的显式转换器。 */
@Component
public class WorkflowConverter {

    /** 组装 Workflow 列表 VO。 */
    public WorkflowVO toListVO(WorkflowEntity entity) {
        return WorkflowVO.builder()
                .id(stringId(entity.getId()))
                .name(entity.getName())
                .code(entity.getCode())
                .description(entity.getDescription())
                .status(entity.getStatus() == null ? null : entity.getStatus().name())
                .publishedVersionId(stringId(entity.getPublishedVersionId()))
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** 组装 Workflow 详情 VO。 */
    public WorkflowDetailVO toDetailVO(WorkflowEntity entity, WorkflowDraftEntity draft, List<WorkflowVersionVO> versions) {
        return WorkflowDetailVO.builder()
                .id(stringId(entity.getId()))
                .name(entity.getName())
                .code(entity.getCode())
                .description(entity.getDescription())
                .status(entity.getStatus() == null ? null : entity.getStatus().name())
                .draft(toDraftVO(draft))
                .publishedVersionId(stringId(entity.getPublishedVersionId()))
                .versions(versions)
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** 组装草稿 VO。 */
    public WorkflowDraftVO toDraftVO(WorkflowDraftEntity draft) {
        if (draft == null) return null;
        return WorkflowDraftVO.builder()
                .id(stringId(draft.getId()))
                .baseVersionId(stringId(draft.getBaseVersionId()))
                .schemaVersion(draft.getSchemaVersion())
                .definition(draft.getDefinition())
                .canvasState(draft.getCanvasState())
                .revision(draft.getRevision())
                .changeNote(draft.getChangeNote())
                .build();
    }

    /** 组装版本 VO。 */
    public WorkflowVersionVO toVersionVO(WorkflowVersionEntity version) {
        return WorkflowVersionVO.builder()
                .id(stringId(version.getId()))
                .workflowId(stringId(version.getWorkflowId()))
                .versionNo(version.getVersionNo())
                .versionName(version.getVersionName())
                .schemaVersion(version.getSchemaVersion())
                .definitionSnapshot(version.getDefinitionSnapshot())
                .canvasSnapshot(version.getCanvasSnapshot())
                .snapshotChecksum(version.getSnapshotChecksum())
                .changeNote(version.getChangeNote())
                .publishedAt(version.getPublishedAt())
                .build();
    }

    private static String stringId(Long id) {
        return Objects.toString(id, null);
    }
}

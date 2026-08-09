package com.lingxi.workbench.admin.workflow.service;

import com.lingxi.workbench.admin.workflow.model.request.CreateWorkflowRequest;
import com.lingxi.workbench.admin.workflow.model.request.PublishWorkflowRequest;
import com.lingxi.workbench.admin.workflow.model.request.RestoreWorkflowDraftRequest;
import com.lingxi.workbench.admin.workflow.model.request.SaveWorkflowDraftRequest;
import com.lingxi.workbench.admin.workflow.model.request.UpdateWorkflowRequest;
import com.lingxi.workbench.admin.workflow.model.request.ValidateWorkflowRequest;
import com.lingxi.workbench.admin.workflow.model.request.WorkflowPageQuery;
import com.lingxi.workbench.admin.workflow.model.vo.WorkflowDetailVO;
import com.lingxi.workbench.admin.workflow.model.vo.WorkflowVO;
import com.lingxi.workbench.admin.workflow.model.vo.WorkflowValidationResultVO;
import com.lingxi.workbench.admin.workflow.model.vo.WorkflowVersionVO;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;

import java.util.Optional;

/** Workflow 管理服务接口。 */
public interface WorkflowService {

    /** 分页查询 Workflow 列表。 */
    PageResult<WorkflowVO> page(WorkflowPageQuery query);

    /** 查询 Workflow 详情，含草稿和版本列表。 */
    WorkflowDetailVO detail(Long id);

    /** 创建 Workflow 并初始化空草稿。 */
    WorkflowDetailVO create(CreateWorkflowRequest request, Long userId);

    /** 更新 Workflow 基本信息。 */
    WorkflowDetailVO update(Long id, UpdateWorkflowRequest request, Long userId);

    /** 保存 Workflow 草稿（乐观锁）。 */
    WorkflowDetailVO saveDraft(Long id, SaveWorkflowDraftRequest request, Long userId);

    /** 完整校验当前草稿及其外部资源引用。 */
    WorkflowValidationResultVO validate(Long id, ValidateWorkflowRequest request);

    /** 发布 Workflow 版本。 */
    WorkflowDetailVO publish(Long id, PublishWorkflowRequest request, Long userId);

    /** 查询指定历史版本的完整快照。 */
    WorkflowVersionVO versionDetail(Long id, Long versionId);

    /** 切换当前生效版本。 */
    void activateVersion(Long id, Long versionId, Long userId);

    /** 将历史版本恢复为新的可编辑草稿 revision。 */
    WorkflowDetailVO restoreDraft(Long id, Long versionId, RestoreWorkflowDraftRequest request, Long userId);

    /** 查询供其他配置业务锁定的 Workflow 正式引用。 */
    Optional<WorkflowReference> findReference(Long id);

    /** 校验指定版本属于目标 Workflow，并返回不可变定义。 */
    Optional<WorkflowDefinition> findVersion(Long workflowId, Long versionId);

    /**
     * @param workflowId Workflow 主键
     * @param active 是否启用
     * @param publishedVersionId 当前生效版本主键
     * @param name Workflow 名称
     */
    record WorkflowReference(Long workflowId, boolean active, Long publishedVersionId, String name) {
    }
}

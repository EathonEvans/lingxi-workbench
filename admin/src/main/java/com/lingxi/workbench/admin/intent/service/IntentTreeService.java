package com.lingxi.workbench.admin.intent.service;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.intent.model.request.CreateIntentTreeRequest;
import com.lingxi.workbench.admin.intent.model.request.IntentTreePageQuery;
import com.lingxi.workbench.admin.intent.model.request.PublishIntentTreeRequest;
import com.lingxi.workbench.admin.intent.model.request.SaveIntentTreeDraftRequest;
import com.lingxi.workbench.admin.intent.model.request.UpdateIntentTreeRequest;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeDetailVO;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeVO;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeValidationResultVO;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeVersionDetailVO;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeVersionVO;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;

import java.util.List;
import java.util.Optional;

/** 意图树草稿、校验和不可变发布版本管理。 */
public interface IntentTreeService {

    PageResult<IntentTreeVO> page(IntentTreePageQuery query);

    IntentTreeDetailVO detail(Long id);

    List<IntentTreeVersionVO> versions(Long id);

    IntentTreeVersionDetailVO versionDetail(Long id, Long versionId);

    IntentTreeDetailVO create(CreateIntentTreeRequest request, Long operatorId);

    IntentTreeDetailVO update(Long id, UpdateIntentTreeRequest request, Long operatorId);

    IntentTreeDetailVO saveDraft(Long id, SaveIntentTreeDraftRequest request, Long operatorId);

    IntentTreeValidationResultVO validate(Long id);

    IntentTreeVersionDetailVO publish(Long id, PublishIntentTreeRequest request, Long operatorId);

    void activateVersion(Long id, Long versionId, Long operatorId);

    void changeStatus(Long id, EnableStatus status, Long operatorId);

    /** 查询供 Workflow 等配置业务锁定的意图树正式引用。 */
    Optional<IntentTreeReference> findReference(Long id);

    /** 校验指定版本属于目标意图树，并返回不可变定义。 */
    Optional<IntentTreeDefinition> findVersion(Long intentTreeId, Long versionId);

    /**
     * 意图树正式引用。
     *
     * @param intentTreeId       意图树主键
     * @param active             是否启用
     * @param publishedVersionId 当前生效版本主键
     * @param name               意图树名称
     */
    record IntentTreeReference(Long intentTreeId, boolean active, Long publishedVersionId, String name) {
    }
}

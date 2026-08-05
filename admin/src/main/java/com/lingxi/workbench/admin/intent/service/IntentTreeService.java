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

import java.util.List;

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
}

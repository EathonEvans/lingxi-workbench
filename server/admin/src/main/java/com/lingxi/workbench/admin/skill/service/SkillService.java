package com.lingxi.workbench.admin.skill.service;

import com.lingxi.workbench.infra.ai.skill.common.enums.SkillLifecycleStatus;
import com.lingxi.workbench.admin.skill.model.request.CreateSkillRequest;
import com.lingxi.workbench.admin.skill.model.request.PublishSkillRequest;
import com.lingxi.workbench.admin.skill.model.request.RevokeSkillVersionRequest;
import com.lingxi.workbench.admin.skill.model.request.SaveSkillDraftRequest;
import com.lingxi.workbench.admin.skill.model.request.SkillPageQuery;
import com.lingxi.workbench.admin.skill.model.request.UpdateSkillRequest;
import com.lingxi.workbench.admin.skill.model.vo.SkillDetailVO;
import com.lingxi.workbench.admin.skill.model.vo.SkillVO;
import com.lingxi.workbench.admin.skill.model.vo.SkillValidationResultVO;
import com.lingxi.workbench.admin.skill.model.vo.SkillVersionDetailVO;
import com.lingxi.workbench.admin.skill.model.vo.SkillVersionVO;
import com.lingxi.workbench.admin.tool.model.vo.ToolOptionVO;
import com.lingxi.workbench.framework.convention.PageResult;

import java.util.List;

/** Skill 控制面业务服务。 */
public interface SkillService {

    PageResult<SkillVO> page(SkillPageQuery query, Long operatorId);

    SkillDetailVO detail(Long id, Long operatorId);

    List<SkillVersionVO> versions(Long id, Long operatorId);

    SkillVersionDetailVO versionDetail(Long id, Long versionId, Long operatorId);

    List<ToolOptionVO> toolOptions();

    SkillDetailVO create(CreateSkillRequest request, Long operatorId);

    SkillDetailVO update(Long id, UpdateSkillRequest request, Long operatorId);

    SkillDetailVO saveDraft(Long id, SaveSkillDraftRequest request, Long operatorId);

    SkillValidationResultVO validate(Long id, Long operatorId);

    SkillVersionDetailVO publish(Long id, PublishSkillRequest request, Long operatorId);

    void activateVersion(Long id, Long versionId, Long operatorId);

    void revokeVersion(Long id, Long versionId, RevokeSkillVersionRequest request, Long operatorId);

    void changeStatus(Long id, SkillLifecycleStatus status, Long operatorId);
}

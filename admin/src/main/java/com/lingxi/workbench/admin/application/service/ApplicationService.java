package com.lingxi.workbench.admin.application.service;

import com.lingxi.workbench.admin.application.model.request.ApplicationPageQuery;
import com.lingxi.workbench.admin.application.model.request.CreateApplicationRequest;
import com.lingxi.workbench.admin.application.model.request.PublishApplicationRequest;
import com.lingxi.workbench.admin.application.model.request.SaveApplicationDraftRequest;
import com.lingxi.workbench.admin.application.model.request.UpdateApplicationRequest;
import com.lingxi.workbench.admin.application.model.vo.ApplicationDetailVO;
import com.lingxi.workbench.admin.application.model.vo.ApplicationVO;
import com.lingxi.workbench.admin.application.model.vo.ApplicationValidationResultVO;
import com.lingxi.workbench.admin.application.model.vo.ApplicationVersionDetailVO;
import com.lingxi.workbench.admin.application.model.vo.ApplicationVersionVO;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.convention.PageResult;

import java.util.List;

/** Application 控制面业务服务。 */
public interface ApplicationService {
    /** 分页查询 Application 列表。 */
    PageResult<ApplicationVO> page(ApplicationPageQuery query);

    /** 查看 Application 编辑详情。 */
    ApplicationDetailVO detail(Long id);

    /** 查看 Application 所有发布版本列表。 */
    List<ApplicationVersionVO> versions(Long id);

    /** 查看指定发布版本的完整 Workflow 快照。 */
    ApplicationVersionDetailVO versionDetail(Long id, Long versionId);

    /** 创建 Application 稳定身份及默认 Workflow 草稿。 */
    ApplicationDetailVO create(CreateApplicationRequest request, Long operatorId);

    /** 修改 Application 名称和描述。 */
    ApplicationDetailVO update(Long id, UpdateApplicationRequest request, Long operatorId);

    /** 保存 Application Workflow 草稿，以 revision 做并发保护。 */
    ApplicationDetailVO saveDraft(
            Long id, SaveApplicationDraftRequest request, Long operatorId);

    /** 对当前草稿执行发布前逐项校验。 */
    ApplicationValidationResultVO validate(Long id);

    /** 校验并发布 Application 当前草稿为不可变版本。 */
    ApplicationVersionDetailVO publish(
            Long id, PublishApplicationRequest request, Long operatorId);

    /** 切换 Application 当前激活的版本。 */
    void activateVersion(Long id, Long versionId, Long operatorId);

    /** 启用或停用 Application。 */
    void changeStatus(Long id, EnableStatus status, Long operatorId);
}

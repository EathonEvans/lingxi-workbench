package com.lingxi.workbench.admin.prompt.service;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.prompt.model.request.PreviewPromptRequest;
import com.lingxi.workbench.admin.prompt.model.request.PromptPageQuery;
import com.lingxi.workbench.admin.prompt.model.request.SavePromptRequest;
import com.lingxi.workbench.admin.prompt.model.vo.PreviewPromptVO;
import com.lingxi.workbench.admin.prompt.model.vo.AvailablePromptVariableVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptDetailVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptVariableVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptVersionVO;
import com.lingxi.workbench.framework.convention.PageResult;

import java.util.List;

/** Prompt模板管理业务。 */
public interface PromptService {

    PageResult<PromptVO> page(PromptPageQuery query);

    PromptDetailVO detail(Long id);

    List<PromptVersionVO> versions(Long id);

    /** 查询当前平台已经具备运行时来源的内置变量。 */
    List<AvailablePromptVariableVO> availableVariables();

    PromptDetailVO save(Long id, SavePromptRequest request, Long operatorId);

    void publish(Long id, Long operatorId);

    void activateVersion(Long id, Long versionId, Long operatorId);

    void changeStatus(Long id, EnableStatus status, Long operatorId);

    PreviewPromptVO preview(PreviewPromptRequest request);
}

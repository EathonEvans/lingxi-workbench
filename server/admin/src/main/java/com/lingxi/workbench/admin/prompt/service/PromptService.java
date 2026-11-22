package com.lingxi.workbench.admin.prompt.service;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableType;
import com.lingxi.workbench.admin.prompt.model.request.PreviewPromptRequest;
import com.lingxi.workbench.admin.prompt.model.request.PromptPageQuery;
import com.lingxi.workbench.admin.prompt.model.request.SavePromptRequest;
import com.lingxi.workbench.admin.prompt.model.vo.PreviewPromptVO;
import com.lingxi.workbench.admin.prompt.model.vo.AvailablePromptVariableCatalogVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptDetailVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptVariableVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptVersionVO;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Prompt模板管理业务。
 */
public interface PromptService {

    /**
     * 分页查询Prompt列表。
     */
    PageResult<PromptVO> page(PromptPageQuery query);

    /**
     * 查询Prompt编辑详情。
     */
    PromptDetailVO detail(Long id);

    /**
     * 查询Prompt历史版本。
     */
    List<PromptVersionVO> versions(Long id);

    /**
     * 查询当前平台已经具备运行时来源的内置变量。
     */
    AvailablePromptVariableCatalogVO availableVariables();

    /**
     * 新增或修改Prompt及其草稿版本。
     */
    PromptDetailVO save(Long id, SavePromptRequest request, Long operatorId);

    /**
     * 发布当前草稿版本。
     */
    void publish(Long id, Long operatorId);

    /**
     * 将指定历史版本切换为正式生效版本。
     */
    void activateVersion(Long id, Long versionId, Long operatorId);

    /**
     * 启用或停用Prompt。
     */
    void changeStatus(Long id, EnableStatus status, Long operatorId);

    /**
     * 使用测试变量渲染Prompt预览。
     */
    PreviewPromptVO preview(PreviewPromptRequest request);

    /** 查询供其他发布配置锁定的 PromptVersion 引用。 */
    Optional<PromptVersionReference> findPublishedVersion(Long versionId);

    /**
     * @param versionId PromptVersion 主键
     * @param role Prompt 角色
     * @param active Prompt 主记录是否启用
     * @param variables Prompt版本冻结的变量声明
     */
    record PromptVersionReference(Long versionId, PromptRole role, boolean active, List<PromptVariableReference> variables) {

        public PromptVersionReference {
            variables = List.copyOf(Objects.requireNonNullElse(variables, List.of()));
        }
    }

    /**
     * @param name 变量名称
     * @param type 变量类型
     */
    record PromptVariableReference(String name, PromptVariableType type) {
    }
}

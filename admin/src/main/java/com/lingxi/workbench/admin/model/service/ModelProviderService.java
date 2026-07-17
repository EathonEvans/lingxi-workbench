package com.lingxi.workbench.admin.model.service;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.model.model.request.ModelProviderPageQuery;
import com.lingxi.workbench.admin.model.model.request.SaveModelProviderRequest;
import com.lingxi.workbench.admin.model.model.vo.ModelProviderVO;
import com.lingxi.workbench.framework.convention.PageResult;

import java.util.List;

/** 模型供应商业务。 */
public interface ModelProviderService {

    /** 分页查询模型供应商。 */
    PageResult<ModelProviderVO> page(ModelProviderPageQuery query);

    /** 查询供应商选项，供关联配置选择。 */
    List<ModelProviderVO> options();

    /** 新增或修改模型供应商。 */
    ModelProviderVO save(Long id, SaveModelProviderRequest request, Long operatorId);

    /** 启用或停用模型供应商。 */
    void changeStatus(Long id, EnableStatus status, Long operatorId);
}

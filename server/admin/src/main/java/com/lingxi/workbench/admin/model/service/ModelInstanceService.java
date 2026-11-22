package com.lingxi.workbench.admin.model.service;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.model.model.bo.ModelInstanceReferenceBO;
import com.lingxi.workbench.admin.model.model.request.ModelInstancePageQuery;
import com.lingxi.workbench.admin.model.model.request.SaveModelInstanceRequest;
import com.lingxi.workbench.admin.model.model.vo.ModelInstanceVO;
import com.lingxi.workbench.framework.convention.PageResult;

import java.util.Optional;

/** 模型实例业务。 */
public interface ModelInstanceService {

    /** 分页查询模型实例。 */
    PageResult<ModelInstanceVO> page(ModelInstancePageQuery query);

    /** 查询可供其他配置业务校验的模型实例引用。 */
    Optional<ModelInstanceReferenceBO> findReference(Long id);

    /** 新增或修改模型实例。 */
    ModelInstanceVO save(Long id, SaveModelInstanceRequest request, Long operatorId);

    /** 启用或停用模型实例。 */
    void changeStatus(Long id, EnableStatus status, Long operatorId);

    /** 设置指定模型类型的默认实例。 */
    void setDefault(Long id, Long operatorId);

    /** 使用当前供应商、密钥和模型配置执行真实连通性测试。 */
    ModelInstanceVO testConnection(Long id, Long operatorId);
}

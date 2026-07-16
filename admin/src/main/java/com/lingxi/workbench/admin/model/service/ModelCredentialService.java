package com.lingxi.workbench.admin.model.service;

import com.lingxi.workbench.admin.model.common.enums.ModelCredentialStatus;
import com.lingxi.workbench.admin.model.model.request.CreateModelCredentialRequest;
import com.lingxi.workbench.admin.model.model.request.RotateModelCredentialRequest;
import com.lingxi.workbench.admin.model.model.vo.ModelCredentialVO;
import com.lingxi.workbench.framework.convention.PageQuery;
import com.lingxi.workbench.framework.convention.PageResult;

import java.util.List;

/** 模型访问密钥业务。 */
public interface ModelCredentialService {

    /** 分页查询模型访问密钥。 */
    PageResult<ModelCredentialVO> page(PageQuery query);

    /** 查询密钥选项，供模型实例选择。 */
    List<ModelCredentialVO> options();

    /** 创建模型访问密钥。 */
    ModelCredentialVO create(CreateModelCredentialRequest request, Long operatorId);

    /** 轮换模型访问密钥。 */
    void rotate(Long id, RotateModelCredentialRequest request, Long operatorId);

    /** 变更模型访问密钥状态。 */
    void changeStatus(Long id, ModelCredentialStatus status, Long operatorId);
}

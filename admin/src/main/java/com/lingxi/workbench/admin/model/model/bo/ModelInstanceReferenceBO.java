package com.lingxi.workbench.admin.model.model.bo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelType;

/**
 * 供其他配置业务校验的模型实例稳定引用。
 *
 * @param id 模型实例主键
 * @param modelType 模型类型
 * @param status 启用状态
 */
public record ModelInstanceReferenceBO(Long id, ModelType modelType, EnableStatus status) {
}

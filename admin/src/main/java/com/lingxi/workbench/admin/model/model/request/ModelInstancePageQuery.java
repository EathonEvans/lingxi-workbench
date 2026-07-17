package com.lingxi.workbench.admin.model.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelType;
import com.lingxi.workbench.framework.convention.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 模型实例分页查询参数。
 */
@Getter
@Setter
public class ModelInstancePageQuery extends PageQuery {

    /**
     * 同时匹配实例名称、实例编码和供应商模型标识。
     */
    @Size(max = 128, message = "搜索关键字不能超过128个字符")
    private String keyword;

    /**
     * 所属供应商主键。
     */
    private Long providerId;

    /**
     * 模型类型。
     */
    private ModelType modelType;

    /**
     * 启停状态。
     */
    private EnableStatus status;
}

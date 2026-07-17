package com.lingxi.workbench.admin.model.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.convention.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 模型供应商分页查询参数。
 */
@Getter
@Setter
public class ModelProviderPageQuery extends PageQuery {

    /**
     * 供应商名称，支持模糊查询。
     */
    @Size(max = 64, message = "供应商名称不能超过64个字符")
    private String name;

    /**
     * 供应商编码，支持模糊查询。
     */
    @Size(max = 64, message = "供应商编码不能超过64个字符")
    private String code;

    /**
     * 启停状态。
     */
    private EnableStatus status;
}

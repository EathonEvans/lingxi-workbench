package com.lingxi.workbench.admin.model.model.request;

import com.lingxi.workbench.admin.model.common.enums.ModelCredentialStatus;
import com.lingxi.workbench.framework.convention.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 模型访问密钥分页查询参数。
 */
@Getter
@Setter
public class ModelCredentialPageQuery extends PageQuery {

    /**
     * Key 名称，支持模糊查询。
     */
    @Size(max = 64, message = "Key名称不能超过64个字符")
    private String name;

    /**
     * 所属供应商主键。
     */
    private Long providerId;

    /**
     * 密钥状态。
     */
    private ModelCredentialStatus status;
}

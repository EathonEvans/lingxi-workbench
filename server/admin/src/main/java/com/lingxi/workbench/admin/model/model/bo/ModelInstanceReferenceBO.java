package com.lingxi.workbench.admin.model.model.bo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelCapability;
import com.lingxi.workbench.admin.model.common.enums.ModelConnectionTestStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

/**
 * 供其他配置业务校验的模型实例稳定引用。
 *
 * <p>同时冻结发布校验需要的供应商协议、能力声明与最近连通状态，
 * 不向配置业务暴露模型或供应商持久化实体。</p>
 */
@Getter
@Builder
public class ModelInstanceReferenceBO {

    private final Long id;

    private final ModelType modelType;

    private final EnableStatus status;

    private final String protocol;

    private final List<ModelCapability> capabilities;

    private final ModelConnectionTestStatus lastTestStatus;

    public Long id() {
        return id;
    }

    public ModelType modelType() {
        return modelType;
    }

    public EnableStatus status() {
        return status;
    }

    public String protocol() {
        return protocol;
    }

    public List<ModelCapability> capabilities() {
        return List.copyOf(Objects.requireNonNullElse(capabilities, List.of()));
    }

    public ModelConnectionTestStatus lastTestStatus() {
        return lastTestStatus;
    }
}

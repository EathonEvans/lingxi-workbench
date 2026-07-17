package com.lingxi.workbench.model.repo;

import com.lingxi.workbench.model.model.bo.ModelRuntimeConfigBO;
import com.lingxi.workbench.model.model.converter.ModelRuntimeConfigConverter;
import com.lingxi.workbench.model.repo.mapper.ModelRuntimeConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 模型运行配置只读数据仓库。 */
@Repository
@RequiredArgsConstructor
public class ModelRuntimeConfigRepo {

    private final ModelRuntimeConfigMapper mapper;
    private final ModelRuntimeConfigConverter converter;

    /**
     * 查询当前可用于对话的默认模型配置。
     *
     * <p>Mapper 返回联合查询实体，Converter 在仓库边界将其转换为业务对象；
     * 未配置可用模型时返回空 Optional，由 Service 转换为明确的业务异常。</p>
     */
    public Optional<ModelRuntimeConfigBO> findDefaultChatModel() {
        return Optional.ofNullable(mapper.selectDefaultChatModel())
                .map(converter::toBO);
    }
}

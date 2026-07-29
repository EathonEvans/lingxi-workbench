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
     */
    public Optional<ModelRuntimeConfigBO> findDefaultChatModel() {
        return Optional.ofNullable(mapper.selectDefaultChatModel())
                .map(converter::toBO);
    }

    /**
     * 按模型实例 ID 查询当前可用的对话模型配置。
     *
     * @param instanceId 模型实例主键
     * @return 模型运行配置业务对象，不存在时返回空 Optional
     */
    public Optional<ModelRuntimeConfigBO> findChatModel(Long instanceId) {
        return Optional.ofNullable(mapper.selectChatModel(instanceId))
                .map(converter::toBO);
    }

    /**
     * 查询库中最新创建的可用 Embedding 模型配置。
     */
    public Optional<ModelRuntimeConfigBO> getEmbedModelConfigBO(Long instanceId) {
        return Optional.ofNullable(mapper.selectEmbeddingModel(instanceId))
                .map(converter::toBO);
    }
}

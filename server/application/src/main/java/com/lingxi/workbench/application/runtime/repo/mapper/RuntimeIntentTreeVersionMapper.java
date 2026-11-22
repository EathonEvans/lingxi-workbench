package com.lingxi.workbench.application.runtime.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import org.apache.ibatis.annotations.Mapper;

/** 意图树发布版本只读 Mapper。 */
@Mapper
public interface RuntimeIntentTreeVersionMapper extends BaseMapper<RuntimeIntentTreeVersionEntity> {
}

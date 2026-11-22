package com.lingxi.workbench.application.runtime.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.application.runtime.model.entity.RuntimePromptVersionEntity;
import org.apache.ibatis.annotations.Mapper;

/** PromptVersion 运行端只读数据访问，禁止修改版本主表。 */
@Mapper
public interface RuntimePromptVersionMapper
        extends BaseMapper<RuntimePromptVersionEntity> {
}

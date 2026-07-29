package com.lingxi.workbench.application.runtime.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeApplicationVersionEntity;
import org.apache.ibatis.annotations.Mapper;

/** ApplicationVersion 运行端只读数据访问，禁止在运行时修改版本主表。 */
@Mapper
public interface RuntimeApplicationVersionMapper extends BaseMapper<RuntimeApplicationVersionEntity> {
}

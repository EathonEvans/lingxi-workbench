package com.lingxi.workbench.application.runtime.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeApplicationEntity;
import org.apache.ibatis.annotations.Mapper;

/** Application 运行端只读数据访问，禁止在运行时修改应用主表。 */
@Mapper
public interface RuntimeApplicationMapper extends BaseMapper<RuntimeApplicationEntity> {
}

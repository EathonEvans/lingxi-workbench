package com.lingxi.workbench.application.runtime.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeToolEntity;
import org.apache.ibatis.annotations.Mapper;

/** Tool 运行端只读数据访问，禁止通过该 Mapper 修改控制面定义。 */
@Mapper
public interface RuntimeToolMapper extends BaseMapper<RuntimeToolEntity> {
}

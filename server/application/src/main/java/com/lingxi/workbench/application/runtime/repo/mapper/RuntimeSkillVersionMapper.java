package com.lingxi.workbench.application.runtime.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeSkillVersionEntity;
import org.apache.ibatis.annotations.Mapper;

/** SkillVersion 运行端只读 Mapper。 */
@Mapper
public interface RuntimeSkillVersionMapper extends BaseMapper<RuntimeSkillVersionEntity> {
}

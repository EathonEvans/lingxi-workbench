package com.lingxi.workbench.application.runtime.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeSkillEntity;
import org.apache.ibatis.annotations.Mapper;

/** Skill 主体运行端只读 Mapper。 */
@Mapper
public interface RuntimeSkillMapper extends BaseMapper<RuntimeSkillEntity> {
}

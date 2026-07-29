package com.lingxi.workbench.application.runtime.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.application.runtime.model.entity.RuntimePromptEntity;
import org.apache.ibatis.annotations.Mapper;

/** Prompt 主记录运行端只读 Mapper。 */
@Mapper
public interface RuntimePromptMapper extends BaseMapper<RuntimePromptEntity> {
}

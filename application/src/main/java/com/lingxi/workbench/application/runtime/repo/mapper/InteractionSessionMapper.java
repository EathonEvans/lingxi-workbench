package com.lingxi.workbench.application.runtime.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.application.runtime.model.entity.InteractionSessionEntity;
import org.apache.ibatis.annotations.Mapper;

/** 通用运行时交互会话Mapper。 */
@Mapper
public interface InteractionSessionMapper extends BaseMapper<InteractionSessionEntity> {
}

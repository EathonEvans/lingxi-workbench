package com.lingxi.workbench.observability.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.observability.model.entity.AiObservationEventEntity;
import org.apache.ibatis.annotations.Mapper;

/** Agent 关键链路事件 Mapper。 */
@Mapper
public interface AiObservationEventMapper extends BaseMapper<AiObservationEventEntity> {
}

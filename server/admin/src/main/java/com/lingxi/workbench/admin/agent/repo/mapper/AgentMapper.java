package com.lingxi.workbench.admin.agent.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.agent.model.entity.AgentEntity;
import org.apache.ibatis.annotations.Mapper;

/** Agent 主记录 Mapper。 */
@Mapper
public interface AgentMapper extends BaseMapper<AgentEntity> {
}

package com.lingxi.workbench.admin.agent.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.agent.model.entity.AgentVersionEntity;
import org.apache.ibatis.annotations.Mapper;

/** Agent 发布版本 Mapper。 */
@Mapper
public interface AgentVersionMapper extends BaseMapper<AgentVersionEntity> {
}

package com.lingxi.workbench.admin.agent.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.agent.model.entity.AgentDraftKnowledgeEntity;
import org.apache.ibatis.annotations.Mapper;

/** Agent 草稿知识库绑定 Mapper。 */
@Mapper
public interface AgentDraftKnowledgeMapper extends BaseMapper<AgentDraftKnowledgeEntity> {
}

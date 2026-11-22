package com.lingxi.workbench.admin.knowledge.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeIndexVersionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 知识索引版本数据访问层。 */
@Mapper
public interface KnowledgeIndexVersionMapper extends BaseMapper<KnowledgeIndexVersionEntity> {

    @Select("SELECT COALESCE(MAX(version_no), 0) + 1 FROM lingxi_core.ai_knowledge_index_version WHERE knowledge_base_id = #{knowledgeBaseId}")
    int nextVersion(Long knowledgeBaseId);
}

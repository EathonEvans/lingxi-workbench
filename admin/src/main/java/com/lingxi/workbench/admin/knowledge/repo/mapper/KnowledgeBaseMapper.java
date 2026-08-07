package com.lingxi.workbench.admin.knowledge.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeBaseEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/** 知识库数据访问层。 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBaseEntity> {

    @Select("SELECT * FROM lingxi_core.ai_knowledge_base WHERE id = #{id} FOR UPDATE")
    Optional<KnowledgeBaseEntity> selectByIdForUpdate(Long id);
}

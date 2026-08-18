package com.lingxi.workbench.admin.agent.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.agent.model.entity.AgentDraftEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** Agent 草稿 Mapper。 */
@Mapper
public interface AgentDraftMapper extends BaseMapper<AgentDraftEntity> {

    /** 使用 revision 实现浏览器并发编辑保护。 */
    @Update("""
            UPDATE lingxi_core.ai_agent_draft
               SET chat_model_instance_id = #{entity.chatModelInstanceId},
                   system_prompt_version_id = #{entity.systemPromptVersionId},
                   user_prompt_version_id = #{entity.userPromptVersionId},
                   tool_codes = #{entity.toolCodes,typeHandler=com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler},
                   skill_ids = #{entity.skillIds,typeHandler=com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler},
                   tool_policy = #{entity.toolPolicy,typeHandler=com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler},
                   retrieval_policy = #{entity.retrievalPolicy,typeHandler=com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler},
                   generation_policy = #{entity.generationPolicy,typeHandler=com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler},
                   plan_policy = #{entity.planPolicy,typeHandler=com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler},
                   change_note = #{entity.changeNote},
                   updated_by = #{entity.updatedBy},
                   revision = revision + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{entity.id}
               AND revision = #{expectedRevision}
            """)
    int isUpdatedWithRevision(
            @Param("entity") AgentDraftEntity entity,
            @Param("expectedRevision") Long expectedRevision);
}

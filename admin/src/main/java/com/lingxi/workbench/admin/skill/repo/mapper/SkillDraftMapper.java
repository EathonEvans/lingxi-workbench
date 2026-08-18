package com.lingxi.workbench.admin.skill.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.skill.model.entity.SkillDraftEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** Skill 草稿 Mapper。 */
@Mapper
public interface SkillDraftMapper extends BaseMapper<SkillDraftEntity> {

    /** 使用 revision 更新 Skill 草稿，防止并发覆盖。 */
    @Update("""
            UPDATE lingxi_core.ai_skill_draft
               SET instructions = #{entity.instructions},
                   activation_mode = #{entity.activationMode},
                   requested_tool_codes = #{entity.requestedToolCodes,typeHandler=com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler},
                   change_note = #{entity.changeNote},
                   updated_by = #{entity.updatedBy},
                   revision = revision + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{entity.id}
               AND revision = #{expectedRevision}
            """)
    int updateWithRevision(@Param("entity") SkillDraftEntity entity, @Param("expectedRevision") Long expectedRevision);
}

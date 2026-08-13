package com.lingxi.workbench.admin.application.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.application.model.entity.AiApplicationDraftEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** Application 草稿 Mapper。 */
@Mapper
public interface AiApplicationDraftMapper extends BaseMapper<AiApplicationDraftEntity> {

    /** 使用 revision 实现浏览器并发编辑保护。 */
    @Update("""
            UPDATE lingxi_core.ai_application_draft
               SET schema_version = #{entity.schemaVersion},
                   definition = #{entity.definition,typeHandler=com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler},
                   change_note = #{entity.changeNote},
                   updated_by = #{entity.updatedBy},
                   revision = revision + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{entity.id}
               AND revision = #{expectedRevision}
            """)
    int isUpdatedWithRevision(
            @Param("entity") AiApplicationDraftEntity entity,
            @Param("expectedRevision") Long expectedRevision);
}

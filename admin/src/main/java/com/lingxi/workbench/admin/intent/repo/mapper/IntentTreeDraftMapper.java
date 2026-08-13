package com.lingxi.workbench.admin.intent.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.intent.model.entity.IntentTreeDraftEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 意图树草稿 Mapper。 */
@Mapper
public interface IntentTreeDraftMapper extends BaseMapper<IntentTreeDraftEntity> {

    /** 使用 revision 实现并发编辑保护。 */
    @Update("""
            UPDATE lingxi_core.ai_intent_tree_draft
               SET schema_version = #{entity.schemaVersion},
                   embedding_model_instance_id = #{entity.embeddingModelInstanceId},
                   definition = #{entity.definition,typeHandler=com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler},
                   change_note = #{entity.changeNote},
                   updated_by = #{entity.updatedBy},
                   revision = revision + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{entity.id}
               AND revision = #{expectedRevision}
            """)
    int isUpdatedWithRevision(@Param("entity") IntentTreeDraftEntity entity, @Param("expectedRevision") Long expectedRevision);
}

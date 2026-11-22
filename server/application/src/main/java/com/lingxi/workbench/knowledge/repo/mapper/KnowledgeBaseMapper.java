package com.lingxi.workbench.knowledge.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.knowledge.model.entity.KnowledgeBaseEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 知识库只读数据访问。
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBaseEntity> {
    /** 在每次检索前读取当前账号资格与知识库授权，禁用和撤权无需重新发布 Agent。 */
    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM lingxi_core.ai_knowledge_base base
                JOIN lingxi_core.sys_user account ON account.id = #{userId} AND account.status = 'ACTIVE'
                JOIN lingxi_core.sys_user_application_access app_access ON app_access.user_id = account.id AND app_access.enabled = TRUE
                WHERE base.id = #{knowledgeBaseId} AND base.status = 'ACTIVE'
                  AND (base.access_mode = 'AUTHENTICATED' OR (base.access_mode = 'RESTRICTED' AND EXISTS (
                      SELECT 1 FROM lingxi_core.ai_knowledge_base_access grant_entry
                      WHERE grant_entry.knowledge_base_id = base.id AND (
                          (grant_entry.subject_type = 'USER' AND grant_entry.subject_id = account.id)
                          OR (grant_entry.subject_type = 'ROLE' AND EXISTS (
                              SELECT 1 FROM lingxi_core.sys_user_role user_role
                              JOIN lingxi_core.sys_role role ON role.id = user_role.role_id AND role.status = 'ACTIVE'
                              WHERE user_role.user_id = account.id AND role.id = grant_entry.subject_id))
                          OR (grant_entry.subject_type = 'DEPARTMENT' AND EXISTS (
                              SELECT 1 FROM lingxi_core.sys_department department
                              WHERE department.id = account.department_id AND department.status = 'ACTIVE'
                                AND department.id = grant_entry.subject_id))
                      )
                  )))
            )
            """)
    boolean canRead(@Param("knowledgeBaseId") Long knowledgeBaseId, @Param("userId") Long userId);

    /** 读取所锁定索引的维度，并同时验证知识库、模型与距离类型。 */
    @Select("""
            SELECT embedding_dimension FROM lingxi_core.ai_knowledge_index_version
            WHERE id = #{indexVersionId} AND knowledge_base_id = #{knowledgeBaseId}
              AND embedding_model_instance_id = #{modelId} AND distance_type = 'COSINE'
            """)
    Integer selectIndexDimension(@Param("knowledgeBaseId") Long knowledgeBaseId,
                                 @Param("indexVersionId") Long indexVersionId, @Param("modelId") Long modelId);
}

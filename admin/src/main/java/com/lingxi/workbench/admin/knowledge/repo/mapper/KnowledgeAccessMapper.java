package com.lingxi.workbench.admin.knowledge.repo.mapper;

import com.lingxi.workbench.admin.knowledge.model.bo.KnowledgeAccessGrant;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 知识库读取授权的批量持久化。 */
@Mapper
public interface KnowledgeAccessMapper {
    @Select("SELECT subject_type, subject_id FROM lingxi_core.ai_knowledge_base_access WHERE knowledge_base_id = #{id} ORDER BY subject_type, subject_id LIMIT 300")
    List<KnowledgeAccessGrant> selectByBaseId(Long id);

    @Delete("DELETE FROM lingxi_core.ai_knowledge_base_access WHERE knowledge_base_id = #{id}")
    void deleteByBaseId(Long id);

    @Insert("""
            <script>
            INSERT INTO lingxi_core.ai_knowledge_base_access (knowledge_base_id, subject_type, subject_id) VALUES
            <foreach collection="grants" item="grant" separator=",">
                (#{id}, #{grant.subjectType}, #{grant.subjectId})
            </foreach>
            </script>
            """)
    void insertAll(@Param("id") Long id, @Param("grants") List<KnowledgeAccessGrant> grants);
}

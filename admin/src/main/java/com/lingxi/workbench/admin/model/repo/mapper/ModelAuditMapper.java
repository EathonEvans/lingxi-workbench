package com.lingxi.workbench.admin.model.repo.mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface ModelAuditMapper {
    @Insert("INSERT INTO lingxi_audit.ai_model_change_log(target_type,target_id,operator_user_id,action,change_summary) VALUES(#{type},#{id},#{operator},#{action},#{summary})")
    int insert(@Param("type") String type, @Param("id") Long id, @Param("operator") Long operator,
               @Param("action") String action, @Param("summary") String summary);
}

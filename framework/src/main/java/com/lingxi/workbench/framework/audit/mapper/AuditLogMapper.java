package com.lingxi.workbench.framework.audit.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 统一操作审计数据访问接口。 */
@Mapper
public interface AuditLogMapper {

    /** 追加审计记录，审计数据只允许新增，不提供修改和删除能力。 */
    @Insert("""
            INSERT INTO lingxi_audit.operation_log
                (business_type, target_type, target_id, operator_user_id, action, change_summary)
            VALUES
                (#{businessType}, #{targetType}, #{targetId}, #{operatorId}, #{action}, #{summary})
            """)
    int insert(@Param("businessType") String businessType,
               @Param("targetType") String targetType,
               @Param("targetId") Long targetId,
               @Param("operatorId") Long operatorId,
               @Param("action") String action,
               @Param("summary") String summary);
}

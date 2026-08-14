package com.lingxi.workbench.admin.domain.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.domain.model.entity.DomainEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 领域表与逻辑引用查询。 */
@Mapper
public interface DomainMapper extends BaseMapper<DomainEntity> {

    @Select("""
            SELECT
                (SELECT COUNT(1) FROM lingxi_core.ai_agent WHERE domain_id = #{id}) +
                (SELECT COUNT(1) FROM lingxi_core.ai_intent_tree WHERE domain_id = #{id}) +
                (SELECT COUNT(1) FROM lingxi_core.ai_application WHERE domain_id = #{id})
            """)
    long countRefs(@Param("id") Long id);
}

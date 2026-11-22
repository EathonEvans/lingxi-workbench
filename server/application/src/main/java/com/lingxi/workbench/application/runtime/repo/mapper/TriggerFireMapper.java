package com.lingxi.workbench.application.runtime.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.application.runtime.model.entity.TriggerFireEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

/** 触发器到期触发记录数据访问层。 */
@Mapper
public interface TriggerFireMapper extends BaseMapper<TriggerFireEntity> {

    /** 消费侧幂等抢占：仅 FIRED 状态可推进到 RUNNING，重复投递直接失败。 */
    @Update("""
            UPDATE lingxi_core.ai_trigger_fire
            SET status = 'RUNNING',
                run_id = #{runId}
            WHERE id = #{id}
              AND status = 'FIRED'
            """)
    int claimRunning(@Param("id") Long id, @Param("runId") String runId);

    /** 推进触发记录终态，仅 RUNNING/FIRED 状态允许推进，防止并发覆盖。 */
    @Update("""
            UPDATE lingxi_core.ai_trigger_fire
            SET status = #{target},
                skip_reason = #{skipReason},
                error_message = #{errorMessage}
            WHERE id = #{id}
              AND status IN ('FIRED', 'RUNNING')
            """)
    int finish(@Param("id") Long id, @Param("target") String target, @Param("skipReason") String skipReason,
               @Param("errorMessage") String errorMessage);

    /** 成功终态并回填关联运行标识。 */
    @Update("""
            UPDATE lingxi_core.ai_trigger_fire
            SET status = 'SUCCEEDED',
                run_id = #{runId}
            WHERE id = #{id}
              AND status IN ('FIRED', 'RUNNING')
            """)
    int markSucceeded(@Param("id") Long id, @Param("runId") String runId);

    /** 对账扫描：触发后长时间没有终态的记录。 */
    @Select("""
            SELECT *
            FROM lingxi_core.ai_trigger_fire
            WHERE status IN ('FIRED', 'RUNNING')
              AND created_at <= #{staleBefore}
            ORDER BY created_at
            LIMIT #{limit}
            """)
    List<TriggerFireEntity> selectStale(@Param("staleBefore") OffsetDateTime staleBefore, @Param("limit") int limit);
}

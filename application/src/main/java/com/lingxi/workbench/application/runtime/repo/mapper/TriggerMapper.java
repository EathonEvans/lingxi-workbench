package com.lingxi.workbench.application.runtime.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.application.runtime.model.entity.TriggerEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

/** 定时触发器数据访问层。 */
@Mapper
public interface TriggerMapper extends BaseMapper<TriggerEntity> {

    /** 扫描到期的调度中触发器。 */
    @Select("""
            SELECT *
            FROM lingxi_core.ai_trigger
            WHERE next_fire_at <= #{now}
              AND status = 'ACTIVE'
              AND deleted_at IS NULL
            ORDER BY next_fire_at
            LIMIT #{limit}
            """)
    List<TriggerEntity> selectDue(@Param("now") OffsetDateTime now, @Param("limit") int limit);

    /**
     * 抢占式推进下次触发时间：仅当 next_fire_at 仍等于扫描读到的值时更新成功，
     * 保证多实例扫描下同一到期时刻只触发一次。
     */
    @Update("""
            UPDATE lingxi_core.ai_trigger
            SET next_fire_at = #{nextFireAt}
            WHERE id = #{id}
              AND next_fire_at = #{expectedNextFireAt}
              AND status = 'ACTIVE'
              AND deleted_at IS NULL
            """)
    int claimNextFire(@Param("id") Long id, @Param("expectedNextFireAt") OffsetDateTime expectedNextFireAt,
                      @Param("nextFireAt") OffsetDateTime nextFireAt);

    /** 记录一次运行结果（终态与最近运行信息），失败计数在数据库侧累加。 */
    @Update("""
            UPDATE lingxi_core.ai_trigger
            SET last_run_id = #{runId},
                last_run_status = #{runStatus},
                last_run_at = #{runAt},
                consecutive_failures = CASE WHEN #{succeeded} THEN 0 ELSE consecutive_failures + 1 END
            WHERE id = #{id}
            """)
    int recordRunResult(@Param("id") Long id, @Param("runId") String runId, @Param("runStatus") String runStatus,
                        @Param("runAt") OffsetDateTime runAt, @Param("succeeded") boolean succeeded);

    /** 达到连续失败阈值后自动暂停；已暂停的触发器不重复更新。 */
    @Update("""
            UPDATE lingxi_core.ai_trigger
            SET status = 'PAUSED',
                pause_reason = #{pauseReason}
            WHERE id = #{id}
              AND status = 'ACTIVE'
              AND consecutive_failures >= #{threshold}
            """)
    int autoPauseOnFailureThreshold(@Param("id") Long id, @Param("threshold") int threshold,
                                    @Param("pauseReason") String pauseReason);

    /** 用户恢复调度：清零失败计数并重算下次触发时间。 */
    @Update("""
            UPDATE lingxi_core.ai_trigger
            SET status = 'ACTIVE',
                pause_reason = NULL,
                consecutive_failures = 0,
                next_fire_at = #{nextFireAt}
            WHERE id = #{id}
              AND status = 'PAUSED'
              AND deleted_at IS NULL
            """)
    int resume(@Param("id") Long id, @Param("nextFireAt") OffsetDateTime nextFireAt);

    /** 统计用户调度中的触发器数量，用于创建时数量上限校验。 */
    @Select("""
            SELECT COUNT(*)
            FROM lingxi_core.ai_trigger
            WHERE user_id = #{userId}
              AND status = 'ACTIVE'
              AND deleted_at IS NULL
            """)
    long countActiveByUser(@Param("userId") Long userId);

    /** 统计用户当日已触发的次数，用于日配额校验。 */
    @Select("""
            SELECT COUNT(*)
            FROM lingxi_core.ai_trigger_fire f
            WHERE f.user_id = #{userId}
              AND f.status IN ('FIRED', 'RUNNING', 'SUCCEEDED', 'FAILED')
              AND f.created_at >= #{dayStart}
            """)
    long countFiredSince(@Param("userId") Long userId, @Param("dayStart") OffsetDateTime dayStart);
}

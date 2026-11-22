package com.lingxi.workbench.notification.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.notification.model.bo.NotificationPayloadBO;
import com.lingxi.workbench.notification.model.entity.NotificationEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;
import java.util.List;

/** 站内通知数据访问层。 */
@Mapper
public interface NotificationMapper extends BaseMapper<NotificationEntity> {

    /**
     * 幂等写入带去重键的通知；同一用户同一去重键只保留最早一条。
     */
    @Insert("""
            INSERT INTO lingxi_core.ai_notification
                (user_id, type, title, content, payload, dedup_key)
            VALUES
                (#{userId}, #{typeCode}, #{title}, #{content},
                 #{payload,typeHandler=com.lingxi.workbench.framework.persistence.handler.GsonJsonbTypeHandler,jdbcType=OTHER},
                 #{dedupKey})
            ON CONFLICT (user_id, dedup_key) WHERE dedup_key IS NOT NULL
                DO NOTHING
            """)
    int insertWithDedupIfAbsent(@Param("userId") Long userId, @Param("typeCode") String typeCode,
                                @Param("title") String title, @Param("content") String content,
                                @Param("payload") NotificationPayloadBO payload, @Param("dedupKey") String dedupKey);

    /** 按去重键将用户对应通知置为已读，返回实际更新的行数。 */
    @Update("""
            UPDATE lingxi_core.ai_notification
            SET read_at = #{readAt}
            WHERE user_id = #{userId}
              AND dedup_key = #{dedupKey}
              AND read_at IS NULL
              AND deleted_at IS NULL
            """)
    int markReadByDedupKey(@Param("userId") Long userId, @Param("dedupKey") String dedupKey,
                           @Param("readAt") OffsetDateTime readAt);

    /** 批量将本人指定通知置为已读，返回实际更新的行数。 */
    @Update("""
            <script>
            UPDATE lingxi_core.ai_notification
            SET read_at = #{readAt}
            WHERE user_id = #{userId}
              AND read_at IS NULL
              AND deleted_at IS NULL
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int markReadBatch(@Param("userId") Long userId, @Param("ids") List<Long> ids,
                      @Param("readAt") OffsetDateTime readAt);

    /** 将本人全部未读通知置为已读，返回实际更新的行数。 */
    @Update("""
            UPDATE lingxi_core.ai_notification
            SET read_at = #{readAt}
            WHERE user_id = #{userId}
              AND read_at IS NULL
              AND deleted_at IS NULL
            """)
    int markAllRead(@Param("userId") Long userId, @Param("readAt") OffsetDateTime readAt);
}

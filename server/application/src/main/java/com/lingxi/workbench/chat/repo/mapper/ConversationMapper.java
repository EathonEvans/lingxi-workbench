package com.lingxi.workbench.chat.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.chat.model.entity.ConversationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 会话数据访问层。 */
@Mapper
public interface ConversationMapper extends BaseMapper<ConversationEntity> {

    /** 锁定用户所属会话，保证同一会话内消息序号连续。 */
    @Select("""
            SELECT *
            FROM lingxi_core.ai_conversation
            WHERE id = #{id}
              AND user_id = #{userId}
              AND deleted_at IS NULL
            FOR UPDATE
            """)
    ConversationEntity selectOwnedForUpdate(
            @Param("id") Long id, @Param("userId") Long userId);

    /** 按首轮客户端消息标识锁定用户所属会话。 */
    @Select("""
            SELECT *
            FROM lingxi_core.ai_conversation
            WHERE user_id = #{userId}
              AND initial_client_message_id = #{clientMessageId}
              AND deleted_at IS NULL
            FOR UPDATE
            """)
    ConversationEntity selectOwnedByInitialMessageIdForUpdate(@Param("userId") Long userId,
                                                               @Param("clientMessageId") String clientMessageId);

    /** 幂等创建首次发送对应的会话；并发冲突时由调用方重新锁定既有记录。 */
    @Insert("""
            INSERT INTO lingxi_core.ai_conversation
                (user_id, initial_client_message_id, title, status, message_count)
            VALUES
                (#{userId}, #{initialClientMessageId}, #{title}, #{status}, #{messageCount})
            ON CONFLICT (user_id, initial_client_message_id)
                WHERE initial_client_message_id IS NOT NULL
            DO NOTHING
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertIfAbsent(ConversationEntity entity);
}

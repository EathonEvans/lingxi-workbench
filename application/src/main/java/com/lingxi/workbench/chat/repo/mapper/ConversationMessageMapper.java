package com.lingxi.workbench.chat.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 会话消息数据访问层。
 */
@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessageEntity> {

    /**
     * 查询会话当前最大消息序号。
     */
    @Select("""
            SELECT COALESCE(MAX(sequence_no), 0)
            FROM lingxi_core.ai_conversation_message
            WHERE conversation_id = #{conversationId}
            """)
    int selectMaxSequence(@Param("conversationId") Long conversationId);

    /**
     * 查询指定序号之前最近的完整问答轮次。
     * 只有状态均为已完成且相邻的 USER、ASSISTANT 消息才会进入记忆。
     */
    @Select("""
            WITH recent_round AS (
                SELECT user_message.sequence_no AS user_sequence_no
                FROM lingxi_core.ai_conversation_message user_message
                INNER JOIN lingxi_core.ai_conversation_message assistant_message
                    ON assistant_message.conversation_id = user_message.conversation_id
                    AND assistant_message.sequence_no = user_message.sequence_no + 1
                    AND assistant_message.role = 'ASSISTANT'
                    AND assistant_message.status = 'COMPLETED'
                WHERE user_message.conversation_id = #{conversationId}
                  AND user_message.sequence_no < #{beforeSequenceNo}
                  AND user_message.role = 'USER'
                  AND user_message.status = 'COMPLETED'
                ORDER BY user_message.sequence_no DESC
                LIMIT #{maxRounds}
            )
            SELECT message.*
            FROM lingxi_core.ai_conversation_message message
            INNER JOIN recent_round round
                ON message.sequence_no = round.user_sequence_no
                OR message.sequence_no = round.user_sequence_no + 1
            WHERE message.conversation_id = #{conversationId}
            ORDER BY message.sequence_no ASC
            """)
    List<ConversationMessageEntity> selectRecentCompletedRounds(@Param("conversationId") Long conversationId,
                                                                @Param("beforeSequenceNo") Integer beforeSequenceNo,
                                                                @Param("maxRounds") int maxRounds);

    /** 查询摘要覆盖位置之后最早的一批完整问答，结果按消息序号正序排列。 */
    @Select("""
            WITH summary_round AS (
                SELECT user_message.sequence_no AS user_sequence_no
                FROM lingxi_core.ai_conversation_message user_message
                INNER JOIN lingxi_core.ai_conversation_message assistant_message
                    ON assistant_message.conversation_id = user_message.conversation_id
                    AND assistant_message.sequence_no = user_message.sequence_no + 1
                    AND assistant_message.role = 'ASSISTANT'
                    AND assistant_message.status = 'COMPLETED'
                WHERE user_message.conversation_id = #{conversationId}
                  AND user_message.sequence_no > #{afterUserSequenceNo}
                  AND user_message.sequence_no <= #{throughUserSequenceNo}
                  AND user_message.role = 'USER'
                  AND user_message.status = 'COMPLETED'
                ORDER BY user_message.sequence_no ASC
                LIMIT #{maxRounds}
            )
            SELECT message.*
            FROM lingxi_core.ai_conversation_message message
            INNER JOIN summary_round round
                ON message.conversation_id = #{conversationId}
                AND (message.sequence_no = round.user_sequence_no
                    OR message.sequence_no = round.user_sequence_no + 1)
            ORDER BY message.sequence_no ASC
            """)
    List<ConversationMessageEntity> selectCompletedRoundsAfter(@Param("conversationId") Long conversationId,
                                                               @Param("afterUserSequenceNo") int afterUserSequenceNo,
                                                               @Param("throughUserSequenceNo") int throughUserSequenceNo,
                                                               @Param("maxRounds") int maxRounds);

    /**
     * 查询当前用户拥有的 Assistant 消息。
     */
    @Select("""
            SELECT message.*
            FROM lingxi_core.ai_conversation_message message
            INNER JOIN lingxi_core.ai_conversation conversation
                ON conversation.id = message.conversation_id
            WHERE message.id = #{messageId}
              AND message.role = 'ASSISTANT'
              AND conversation.user_id = #{userId}
              AND conversation.deleted_at IS NULL
            """)
    Optional<ConversationMessageEntity> selectOwnedAssistantMessage(@Param("messageId") Long messageId, @Param("userId") Long userId);
}

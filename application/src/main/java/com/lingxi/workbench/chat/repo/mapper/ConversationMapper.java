package com.lingxi.workbench.chat.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.chat.model.entity.ConversationEntity;
import org.apache.ibatis.annotations.Mapper;
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
}

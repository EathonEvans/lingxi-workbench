package com.lingxi.workbench.chat.memory.repo;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.chat.memory.model.entity.UserProfileEntity;
import com.lingxi.workbench.chat.memory.repo.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * 用户画像数据仓库。
 */
@Repository
@RequiredArgsConstructor
public class UserProfileRepo {
    private final UserProfileMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 按 userId 查询画像。
     */
    public Optional<UserProfileEntity> findByUserId(Long userId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<UserProfileEntity>lambdaQuery()
                        .eq(UserProfileEntity::getUserId, userId)));
    }

    /**
     * 插入或更新画像（userId 唯一约束）。
     */
    public void upsert(Long userId, Long sourceMessageId, Map<String, Object> profile) {
        jdbcTemplate.update(
                """
                INSERT INTO lingxi_core.ai_user_profile
                    (user_id, profile, version, source_message_id)
                VALUES (?, ?::jsonb, 1, ?)
                ON CONFLICT (user_id) DO UPDATE
                SET profile = EXCLUDED.profile,
                    version = ai_user_profile.version + 1,
                    source_message_id = EXCLUDED.source_message_id,
                    updated_at = CURRENT_TIMESTAMP
                WHERE ai_user_profile.source_message_id IS NULL
                   OR ai_user_profile.source_message_id < EXCLUDED.source_message_id
                """,
                userId,
                JSON.toJSONString(profile),
                sourceMessageId);
    }
}

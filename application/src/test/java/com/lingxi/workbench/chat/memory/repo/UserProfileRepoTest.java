package com.lingxi.workbench.chat.memory.repo;

import com.lingxi.workbench.chat.memory.repo.mapper.UserProfileMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserProfileRepoTest {

    private final UserProfileMapper mapper = mock(UserProfileMapper.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final UserProfileRepo repo = new UserProfileRepo(mapper, jdbcTemplate);

    @Test
    void shouldMergeProfileAtomicallyWithMessageWatermark() {
        repo.merge(7L, 12L, Map.of("city", "杭州"));

        verify(jdbcTemplate).update(
                contains("COALESCE(ai_user_profile.profile, '{}'::jsonb) || EXCLUDED.profile"),
                eq(7L), contains("\"city\":\"杭州\""), eq(12L));
    }

    @Test
    void shouldReplaceProfileWithMessageWatermark() {
        repo.replace(7L, 12L, Map.of("city", "杭州"));

        verify(jdbcTemplate).update(
                contains("SET profile = EXCLUDED.profile"),
                eq(7L), contains("\"city\":\"杭州\""), eq(12L));
    }
}

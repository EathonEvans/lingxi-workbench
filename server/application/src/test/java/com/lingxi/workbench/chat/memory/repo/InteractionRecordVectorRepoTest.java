package com.lingxi.workbench.chat.memory.repo;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InteractionRecordVectorRepoTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final InteractionRecordVectorRepo repo = new InteractionRecordVectorRepo(jdbcTemplate);

    @Test
    void shouldInsertPgvectorLiteralWithExplicitCast() {
        repo.insert(11L, 21L, new float[]{0.25F, -0.5F, 1.0F});

        verify(jdbcTemplate).update(
                contains("VALUES (?, ?, ?, ?::vector)"),
                eq(11L), eq(21L), eq(3), eq("[0.25,-0.5,1.0]"));
    }
}

package com.lingxi.workbench.infra.ai.chat.model.bo;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TokenUsageTest {

    @Test
    void shouldCalculateTotalTokensWhenProviderOmitsTotal() {
        TokenUsage usage = TokenUsage.of(12, 4, null);

        assertEquals(16, usage.totalTokens());
    }

    @Test
    void shouldAccumulateNullableTokenUsage() {
        TokenUsage usage = TokenUsage.of(10, 3, 13).plus(TokenUsage.of(5, 2, 7));

        assertEquals(TokenUsage.of(15, 5, 20), usage);
    }

    @Test
    void shouldInitializeAccumulateAndResetThreadSafely() {
        TokenUsageAccumulator accumulator = new TokenUsageAccumulator();
        accumulator.initialize(TokenUsage.of(2, 1, 3));
        IntStream.range(0, 1000).parallel().forEach(index -> accumulator.add(TokenUsage.of(1, 1, 2)));

        assertEquals(TokenUsage.of(1002, 1001, 2003), accumulator.snapshot());

        accumulator.reset();
        assertFalse(accumulator.snapshot().hasUsage());
    }
}

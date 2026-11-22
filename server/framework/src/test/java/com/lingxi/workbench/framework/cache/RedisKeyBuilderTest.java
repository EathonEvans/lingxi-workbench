package com.lingxi.workbench.framework.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedisKeyBuilderTest {

    @Test
    void shouldBuildKeyFromMultipleSegments() {
        assertEquals("lx_wbc:user:42", RedisKeyBuilder.build("user", "42"));
    }

    @Test
    void shouldNormalizeWhitespaceAndBoundarySeparators() {
        assertEquals("lx_wbc:user:profile:42", RedisKeyBuilder.build(" :user: ", "profile", ":42::"));
    }

    @Test
    void shouldRejectMissingSegments() {
        assertThrows(IllegalArgumentException.class, () -> RedisKeyBuilder.build((String[]) null));
        assertThrows(IllegalArgumentException.class, RedisKeyBuilder::build);
    }

    @Test
    void shouldRejectBlankOrSeparatorOnlySegment() {
        assertThrows(IllegalArgumentException.class, () -> RedisKeyBuilder.build("user", " "));
        assertThrows(IllegalArgumentException.class, () -> RedisKeyBuilder.build("user", ":::"));
    }
}

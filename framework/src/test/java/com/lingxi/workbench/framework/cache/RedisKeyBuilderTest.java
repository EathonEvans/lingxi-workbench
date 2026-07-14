package com.lingxi.workbench.framework.cache;

import com.lingxi.workbench.framework.exceptions.SysException;
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
        assertThrows(SysException.class, () -> RedisKeyBuilder.build((String[]) null));
        assertThrows(SysException.class, RedisKeyBuilder::build);
    }

    @Test
    void shouldRejectBlankOrSeparatorOnlySegment() {
        assertThrows(SysException.class, () -> RedisKeyBuilder.build("user", " "));
        assertThrows(SysException.class, () -> RedisKeyBuilder.build("user", ":::"));
    }
}

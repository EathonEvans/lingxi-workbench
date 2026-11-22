package com.lingxi.workbench.chat.memory.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryDataTest {

    @Test
    void shouldBoundKeyCountAndLength() {
        Map<String, Object> source = new LinkedHashMap<>();
        for (int i = 0; i < MemoryData.MAX_KEYS + 10; i++) {
            source.put("key" + i, "value");
        }
        source.put("x".repeat(MemoryData.MAX_KEY_LENGTH + 10), "should-drop");

        MemoryData data = MemoryData.from(source);

        assertEquals(MemoryData.MAX_KEYS, data.asObjectMap().size());
        assertFalse(data.asObjectMap().containsKey("x".repeat(MemoryData.MAX_KEY_LENGTH + 10)));
    }

    @Test
    void shouldTruncateOverlongValue() {
        Map<String, Object> source = Map.of("note", "a".repeat(MemoryData.MAX_VALUE_LENGTH + 100));

        MemoryData data = MemoryData.from(source);

        assertEquals(MemoryData.MAX_VALUE_LENGTH, ((String) data.asObjectMap().get("note")).length());
    }

    @Test
    void shouldMergeWithOverwrite() {
        MemoryData existing = MemoryData.from(Map.of("language", "zh-CN", "city", "杭州"));
        MemoryData extracted = MemoryData.from(Map.of("city", "北京", "role", "管理员"));

        MemoryData merged = existing.merge(extracted);

        assertEquals("zh-CN", merged.asObjectMap().get("language"));
        assertEquals("北京", merged.asObjectMap().get("city"));
        assertEquals("管理员", merged.asObjectMap().get("role"));
    }

    @Test
    void shouldReturnEmptyForInvalidJson() {
        MemoryData data = MemoryData.fromJson("not-a-json");

        assertTrue(data.isEmpty());
        assertEquals("{}", data.toPromptJson());
    }

    @Test
    void shouldParseValidJsonAndBoundSerialization() {
        MemoryData data = MemoryData.fromJson("{\"language\":\"zh-CN\",\"city\":\"杭州\"}");

        assertEquals("zh-CN", data.asObjectMap().get("language"));
        assertTrue(data.toPromptJson().length() <= MemoryData.MAX_SERIALIZED_LENGTH);
    }
}

package com.lingxi.workbench.framework.cache;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.framework.exceptions.SysException;

import static com.lingxi.workbench.framework.cache.RedisKeyConstants.CACHE_PREFIX;

public final class RedisKeyBuilder {

    private static final String SEPARATOR = ":";

    private RedisKeyBuilder() {
    }

    /**
     * 构建redisKey
     *
     * @param keyValues key关键词
     * @return 完整key
     */
    public static String build(String... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            throw new SysException("keyValues must not be null or empty");
        }

        StringBuilder builder = new StringBuilder(CACHE_PREFIX);

        for (String keyValue : keyValues) {
            String segment = normalizeSegment(keyValue);
            if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != SEPARATOR.charAt(0)) {
                builder.append(SEPARATOR);
            }
            builder.append(segment);
        }

        return builder.toString();
    }

    private static String normalizeSegment(String keyValue) {
        if (CharSequenceUtil.isBlank(keyValue)) {
            throw new SysException("keyValue must not be blank");
        }

        String segment = keyValue.trim();
        int start = 0;
        int end = segment.length();

        while (start < end && segment.charAt(start) == SEPARATOR.charAt(0)) {
            start++;
        }
        while (end > start && segment.charAt(end - 1) == SEPARATOR.charAt(0)) {
            end--;
        }

        segment = segment.substring(start, end).trim();
        if (segment.isEmpty()) {
            throw new SysException("keyValue must contain a non-separator character");
        }
        return segment;
    }
}

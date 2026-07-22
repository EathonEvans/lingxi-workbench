package com.lingxi.workbench.framework.cache;

import cn.hutool.core.text.CharSequenceUtil;

import static com.lingxi.workbench.framework.cache.RedisKeyConstants.CACHE_PREFIX;

/**
 * Redis Key 构建工具，统一添加业务前缀并规范化拼接片段。
 *
 * <p>所有 Key 自动拼接 {@link RedisKeyConstants#CACHE_PREFIX} 前缀，
 * 避免不同服务或业务模块的 Key 冲突。</p>
 */
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
            throw new IllegalArgumentException("Redis Key 片段不能为空");
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

    /**
     * 规范化 Key 片段，去除首尾空白与分隔符，确保拼接后 Key 格式一致。
     */
    private static String normalizeSegment(String keyValue) {
        if (CharSequenceUtil.isBlank(keyValue)) {
            throw new IllegalArgumentException("Redis Key 片段不能是空白字符串");
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
            throw new IllegalArgumentException("Redis Key 片段不能只包含分隔符");
        }
        return segment;
    }
}

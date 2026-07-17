package com.lingxi.workbench.chat.model.bo;

/**
 * 单次模型回答的 Token 用量。
 *
 * <p>供应商未返回用量时字段保持为空，禁止使用估算值伪装成模型实际计费数据。</p>
 */
public record MessageTokenUsageBO(
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens) {

    /** 创建供应商未返回用量时的空对象。 */
    public static MessageTokenUsageBO empty() {
        return new MessageTokenUsageBO(null, null, null);
    }
}

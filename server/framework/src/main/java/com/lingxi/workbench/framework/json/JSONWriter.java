package com.lingxi.workbench.framework.json;

/**
 * Gson JSON 输出策略的项目内表达。
 *
 * <p>该枚举只保留当前代码真实使用的两种输出特性，避免把供应商库的完整 API
 * 泄露到业务模块。调用方仍然可以明确表达“格式化输出”和“Long 按字符串输出”
 * 这两个已有业务约束。</p>
 */
public final class JSONWriter {

    private JSONWriter() {
    }

    /** JSON 文本输出时可组合的格式策略。 */
    public enum Feature {

        /** 输出带缩进和换行的 JSON，便于评估数据集和快照人工阅读。 */
        PrettyFormat,

        /** 将 Long 按字符串输出，避免前端 JavaScript 数字精度丢失。 */
        WriteLongAsString
    }
}

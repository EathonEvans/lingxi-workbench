package com.lingxi.workbench.workflow.model.intent;

/**
 * 意图路由未命中或澄清失败时的用户可见兜底文案。
 *
 * <p>这两个字段由管理员在 Application 配置时填写，发布后即不可变。
 * 运行时由 {@code IntentRoutingServiceImpl} 在以下场景直接返回：</p>
 * <ul>
 *   <li>{@code unmatchedResponse} — 向量召回无任何候选通过阈值；
 *       或所有候选被过滤后为空</li>
 *   <li>{@code clarificationFailureResponse} — 澄清会话超轮次（V1 最大 2 轮）、
 *       用户主动取消（回复"算了"、"取消"等关键词）或会话过期（10 分钟）</li>
 * </ul>
 *
 * <p>未命中不等同于闲聊。闲聊必须建模为显式的高置信度叶子意图，
 * 而非依赖兜底文案充当通用回复。</p>
 *
 * @param unmatchedResponse            未召回任何可信意图时的答复
 * @param clarificationFailureResponse 澄清超限、取消或过期后的答复
 */
public record IntentFallbackPolicy(
        String unmatchedResponse,
        String clarificationFailureResponse) {
}
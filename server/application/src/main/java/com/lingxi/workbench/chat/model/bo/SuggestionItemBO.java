package com.lingxi.workbench.chat.model.bo;

/**
 * 回答完成后的单条主动建议。
 *
 * <p>建议只承载“可点击发送的后续问题”，不携带结构化工具调用参数；
 * 用户点击后以 {@code prompt} 作为普通输入续接会话，走既有意图路由与能力执行链路。</p>
 *
 * @param id     服务端生成的建议标识，用于前端渲染去重
 * @param title  建议的短标题，展示在建议卡片上
 * @param prompt 点击建议后实际发送的完整问题文本
 */
public record SuggestionItemBO(String id, String title, String prompt) {
}

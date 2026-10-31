package com.lingxi.workbench.chat.model.bo;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 回答完成后的主动建议事件，通过 SSE 的 {@code suggestion} 事件类型发给前端。
 *
 * <p>该事件属于完成终态前的补充事件：回答已落库、{@code completed} 事件尚未发布。
 * 前端将建议渲染为可点击的后续问题卡片，点击后以建议的 {@code prompt} 续接会话。
 * 建议是流内瞬时卡片，不随历史消息持久化，刷新后不再展示。</p>
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatSuggestionEventBO {

    /** SSE 消息序号，用于客户端去重和断线续传。 */
    @Setter
    private long sequence;

    /** 本次回答的主动建议列表，评估失败或无建议时为空列表。 */
    private List<SuggestionItemBO> suggestions;
}

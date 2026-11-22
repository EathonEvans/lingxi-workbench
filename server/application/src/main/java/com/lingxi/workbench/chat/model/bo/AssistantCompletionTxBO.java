package com.lingxi.workbench.chat.model.bo;

import com.lingxi.workbench.chat.model.message.ConversationCompletedMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Assistant 完成事务的进程内参数，不写入 RocketMQ 消息体。
 *
 * <p>定时触发器路径携带 triggerId/fireId，由本地事务监听器在同一事务内推进触发记录终态。</p>
 */
@Getter
@Builder
@EqualsAndHashCode
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AssistantCompletionTxBO {

    /** 只包含业务定位信息的事务事件。 */
    private final ConversationCompletedMessage event;
    /** 已生成的完整回答。 */
    private final String content;
    /** 本轮累计 Token 用量。 */
    private final TokenUsage tokenUsage;
    /** 模型实例与非完整收口说明。 */
    private final AssistantCompletionMetaBO metadata;
    /** 定时触发器主键；用户对话路径为空。 */
    private final Long triggerId;
    /** 定时触发器的单次触发记录主键；用户对话路径为空。 */
    private final Long fireId;

    /** 构建不携带触发器上下文的用户对话完成事务参数。 */
    public static AssistantCompletionTxBO of(ConversationCompletedMessage event, String content,
                                             TokenUsage tokenUsage, AssistantCompletionMetaBO metadata) {
        return AssistantCompletionTxBO.builder()
                .event(event)
                .content(content)
                .tokenUsage(tokenUsage)
                .metadata(metadata)
                .build();
    }
}

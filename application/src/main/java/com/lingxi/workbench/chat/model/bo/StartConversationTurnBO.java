package com.lingxi.workbench.chat.model.bo;

import com.lingxi.workbench.chat.common.enums.MessageOrigin;
import lombok.Builder;
import lombok.Getter;

/**
 * 首次创建或继续已有会话时，持久化一轮消息所需的完整业务输入。
 */
@Getter
@Builder
public class StartConversationTurnBO {

    private final Long userId;
    /**
     * 已存在会话的服务端主键；为空时创建新会话。
     */
    private final String conversationId;
    private final String clientMessageId;
    private final String prompt;
    private final Long modelInstanceId;
    private final Long applicationId;
    private final Long applicationVersionId;
    /**
     * 消息来源；为空时按用户对话（CHAT）处理，定时触发器路径显式传 TRIGGER。
     */
    private final MessageOrigin origin;

    /** 解析实际生效的消息来源，缺省为用户对话。 */
    public MessageOrigin resolveOrigin() {
        return origin == null ? MessageOrigin.CHAT : origin;
    }
}

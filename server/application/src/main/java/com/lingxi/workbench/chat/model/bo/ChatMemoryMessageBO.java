package com.lingxi.workbench.chat.model.bo;

import com.lingxi.workbench.chat.common.enums.MessageRole;

/**
 * application 记忆模块选出的一条历史消息。
 *
 * @param sequenceNo 数据库消息序号；测试或兼容调用无法提供时为空
 * @param role       数据库消息角色
 * @param content    已完成消息的完整文本内容
 */
public record ChatMemoryMessageBO(Integer sequenceNo, MessageRole role, String content) {

    /**
     * 兼容不关心摘要覆盖范围的调用方。
     */
    public ChatMemoryMessageBO(MessageRole role, String content) {
        this(null, role, content);
    }
}

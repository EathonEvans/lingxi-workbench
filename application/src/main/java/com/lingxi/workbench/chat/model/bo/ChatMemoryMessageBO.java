package com.lingxi.workbench.chat.model.bo;

import com.lingxi.workbench.chat.common.enums.MessageRole;

/**
 * application 记忆模块选出的一条历史消息。
 *
 * @param role 数据库消息角色
 * @param content 已完成消息的完整文本内容
 */
public record ChatMemoryMessageBO(MessageRole role, String content) {}

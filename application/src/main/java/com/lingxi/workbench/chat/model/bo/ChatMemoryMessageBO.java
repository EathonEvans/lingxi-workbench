package com.lingxi.workbench.chat.model.bo;

import com.lingxi.workbench.chat.common.enums.MessageRole;

/** 注入模型上下文的一条历史消息。 */
public record ChatMemoryMessageBO(MessageRole role, String content) {}

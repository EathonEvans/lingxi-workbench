package com.lingxi.workbench.chat.model.bo;

import com.lingxi.workbench.chat.model.vo.ChatStartVO;
import reactor.core.publisher.Flux;

/** 已持久化消息标识及独立生成任务的事件流。 */
public record ChatStreamResult(ChatStartVO start, Flux<ChatGenerationEventBO> events) {}

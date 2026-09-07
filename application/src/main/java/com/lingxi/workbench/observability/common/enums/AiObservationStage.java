package com.lingxi.workbench.observability.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 一轮 Application Chat 的稳定观测阶段。
 */
@Getter
@RequiredArgsConstructor
public enum AiObservationStage {
    MESSAGE_PERSIST("MESSAGE_PERSIST", "消息落库"),
    CONTEXT_PREPARE("CONTEXT_PREPARE", "上下文准备"),
    INTENT_ROUTE("INTENT_ROUTE", "意图路由"),
    KNOWLEDGE_SEARCH("KNOWLEDGE_SEARCH", "知识检索"),
    KNOWLEDGE_AUTHORIZATION("KNOWLEDGE_AUTHORIZATION", "知识读取授权"),
    KNOWLEDGE_EMBEDDING("KNOWLEDGE_EMBEDDING", "检索查询向量"),
    KNOWLEDGE_RECALL("KNOWLEDGE_RECALL", "知识候选召回"),
    KNOWLEDGE_RERANK("KNOWLEDGE_RERANK", "知识候选重排序"),
    EXECUTION("EXECUTION", "目标执行"),
    TERMINAL("TERMINAL", "结果收口"),
    MEMORY_POST("MEMORY_POST", "记忆后处理");

    @EnumValue
    private final String code;
    private final String describe;

    public static AiObservationStage fromCode(String code) {
        for (AiObservationStage stage : values()) {
            if (stage.code.equals(code)) {
                return stage;
            }
        }
        throw new IllegalArgumentException("不支持的Agent观测阶段：" + code);
    }
}

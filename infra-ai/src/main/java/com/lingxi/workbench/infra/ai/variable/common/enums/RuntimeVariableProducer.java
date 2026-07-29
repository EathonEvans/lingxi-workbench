package com.lingxi.workbench.infra.ai.variable.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 首次产生运行时变量的处理阶段。 */
@Getter
@RequiredArgsConstructor
public enum RuntimeVariableProducer {

    REQUEST_INITIALIZATION("REQUEST_INITIALIZATION", "请求初始化"),
    CONVERSATION_HISTORY("CONVERSATION_HISTORY", "会话历史加载"),
    QUERY_REWRITE("QUERY_REWRITE", "问题改写"),
    KNOWLEDGE_RETRIEVAL("KNOWLEDGE_RETRIEVAL", "知识检索");

    /** 稳定生产阶段编码。 */
    private final String code;
    /** 中文说明。 */
    private final String describe;
}

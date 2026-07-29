package com.lingxi.workbench.admin.agent.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Agent 类型。V1 只支持知识库问答 Agent。 */
@Getter
@RequiredArgsConstructor
public enum AgentType {
    RAG_CHAT("RAG_CHAT", "知识库问答");

    @EnumValue
    private final String code;
    private final String describe;
}

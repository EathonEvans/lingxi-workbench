package com.lingxi.workbench.admin.agent.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Agent 类型决定发布校验规则和运行时前置处理链。 */
@Getter
@RequiredArgsConstructor
public enum AgentType {
    REACT("REACT", "ReAct Agent"),
    RAG_CHAT("RAG_CHAT", "知识库问答"),
    SUPERVISOR("SUPERVISOR", "多Agent监督者");

    @EnumValue
    private final String code;
    private final String describe;
}

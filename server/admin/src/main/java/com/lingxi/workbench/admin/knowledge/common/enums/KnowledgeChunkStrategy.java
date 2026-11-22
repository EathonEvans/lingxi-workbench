package com.lingxi.workbench.admin.knowledge.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 知识库文档切片策略。 */
@Getter
@RequiredArgsConstructor
public enum KnowledgeChunkStrategy {
    FIXED("FIXED", "固定长度"),
    PARAGRAPH("PARAGRAPH", "段落优先"),
    HEADING("HEADING", "标题感知");

    @EnumValue
    private final String code;
    private final String describe;
}

package com.lingxi.workbench.admin.knowledge.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 知识库文档处理状态。 */
@Getter
@RequiredArgsConstructor
public enum KnowledgeDocumentStatus {
    UPLOADED("UPLOADED", "等待解析"),
    PARSING("PARSING", "解析中"),
    PARSED("PARSED", "待分块"),
    FAILED("FAILED", "解析失败");

    @EnumValue
    private final String code;
    private final String describe;
}

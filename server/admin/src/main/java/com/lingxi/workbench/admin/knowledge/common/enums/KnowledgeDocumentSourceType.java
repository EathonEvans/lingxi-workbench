package com.lingxi.workbench.admin.knowledge.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 知识库文档来源类型。 */
@Getter
@RequiredArgsConstructor
public enum KnowledgeDocumentSourceType {
    FILE("FILE", "文件上传"),
    TEXT("TEXT", "文本录入");

    @EnumValue
    private final String code;
    private final String describe;
}

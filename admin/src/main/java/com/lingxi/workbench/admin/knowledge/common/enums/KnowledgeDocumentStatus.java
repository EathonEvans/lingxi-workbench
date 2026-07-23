package com.lingxi.workbench.admin.knowledge.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 知识库文档处理状态。 */
@Getter
@RequiredArgsConstructor
public enum KnowledgeDocumentStatus {
    PENDING("PENDING", "待处理"),
    RUNNING("RUNNING", "处理中"),
    SUCCESS("SUCCESS", "处理成功"),
    FAILED("FAILED", "处理失败");

    @EnumValue
    private final String code;
    private final String describe;
}

package com.lingxi.workbench.admin.knowledge.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 文档分块与向量化任务状态。 */
@Getter
@RequiredArgsConstructor
public enum KnowledgeChunkTaskStatus {
    PENDING("PENDING", "等待处理"),
    PARSING("PARSING", "正在解析"),
    CHUNKING("CHUNKING", "正在分块"),
    VECTORIZING("VECTORIZING", "正在向量化"),
    SUCCEEDED("SUCCEEDED", "处理完成"),
    FAILED("FAILED", "处理失败");

    @EnumValue
    private final String code;
    private final String describe;

    /** 是否已经进入不可继续推进的终态。 */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}

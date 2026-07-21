package com.lingxi.workbench.admin.knowledge.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 文档解析任务状态。 */
@Getter
@RequiredArgsConstructor
public enum KnowledgeJobStatus {
    PENDING("PENDING", "等待执行"),
    RUNNING("RUNNING", "执行中"),
    SUCCEEDED("SUCCEEDED", "执行成功"),
    FAILED("FAILED", "执行失败");

    @EnumValue
    private final String code;
    private final String describe;
}

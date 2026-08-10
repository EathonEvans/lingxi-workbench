package com.lingxi.workbench.application.runtime.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 单次知识检索相对当前 Agent 执行证据集的状态。
 */
@Getter
@RequiredArgsConstructor
public enum KnowledgeSearchStatus {

    FOUND("FOUND", "获得新证据"),
    EMPTY("EMPTY", "未命中证据"),
    SATURATED("SATURATED", "证据已饱和");

    /** 稳定编码。 */
    private final String code;
    /** 中文说明。 */
    private final String describe;
}

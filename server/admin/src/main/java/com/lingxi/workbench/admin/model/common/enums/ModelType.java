package com.lingxi.workbench.admin.model.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 平台支持的模型类型。 */
@Getter
@RequiredArgsConstructor
public enum ModelType {
    /** 对话生成模型。 */
    CHAT("CHAT", "对话模型"),
    /** 文本向量化模型。 */
    EMBEDDING("EMBEDDING", "向量模型"),
    /** 检索结果重排序模型。 */
    RERANK("RERANK", "重排序模型");

    /** 稳定编码，同时作为数据库存储值。 */
    @EnumValue
    private final String code;
    /** 中文说明。 */
    private final String describe;
}

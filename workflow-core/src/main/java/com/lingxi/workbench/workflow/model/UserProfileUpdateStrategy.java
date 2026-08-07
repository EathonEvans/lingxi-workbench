package com.lingxi.workbench.workflow.model;

/** 用户画像更新方式。 */
public enum UserProfileUpdateStrategy {
    /** 保留未被本次提取覆盖的既有字段，并更新同名字段。 */
    MERGE,
    /** 使用本次提取结果完全替换既有画像。 */
    REPLACE
}

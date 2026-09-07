package com.lingxi.workbench.admin.knowledge.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 应用端知识读取范围，不赋予管理端操作权限。 */
@Getter
@RequiredArgsConstructor
public enum KnowledgeAccessMode {
    AUTHENTICATED("AUTHENTICATED", "全部应用用户"),
    RESTRICTED("RESTRICTED", "指定用户范围");
    @EnumValue
    private final String code;
    private final String describe;
}

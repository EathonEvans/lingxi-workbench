package com.lingxi.workbench.admin.knowledge.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 可被授予知识读取权限的账号目录主体。 */
@Getter
@RequiredArgsConstructor
public enum KnowledgeAccessSubjectType {
    USER("USER", "用户"), ROLE("ROLE", "角色"), DEPARTMENT("DEPARTMENT", "部门");
    @EnumValue
    private final String code;
    private final String describe;
}

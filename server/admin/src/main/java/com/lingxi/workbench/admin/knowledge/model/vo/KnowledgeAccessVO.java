package com.lingxi.workbench.admin.knowledge.model.vo;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeAccessMode;

import java.util.Set;

/**
 * 当前实时读取授权，三类主体取并集，部门仅包括直属成员。
 * @param mode 访问范围
 * @param userIds 指定用户
 * @param roleIds 指定角色
 * @param departmentIds 指定部门
 */
public record KnowledgeAccessVO(KnowledgeAccessMode mode, Set<Long> userIds, Set<Long> roleIds, Set<Long> departmentIds) {
}

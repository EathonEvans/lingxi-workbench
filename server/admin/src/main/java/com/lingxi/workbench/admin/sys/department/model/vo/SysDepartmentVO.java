package com.lingxi.workbench.admin.sys.department.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 部门树节点响应模型。
 *
 * @param id        部门主键
 * @param parentId  上级部门主键
 * @param name      部门名称
 * @param code      部门编码
 * @param sortOrder 同级排序序号
 * @param status    启用状态
 * @param children  子部门列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SysDepartmentVO {
    /** 部门主键。 */
    private Long id;
    /** 上级部门主键。 */
    private Long parentId;
    /** 部门名称。 */
    private String name;
    /** 部门编码。 */
    private String code;
    /** 同级排序序号。 */
    private Integer sortOrder;
    /** 启用状态。 */
    private EnableStatus status;
    /** 子部门列表。 */
    private List<SysDepartmentVO> children;
}
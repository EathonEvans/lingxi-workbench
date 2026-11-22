package com.lingxi.workbench.admin.sys.role.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.common.enums.RoleDataScope;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色信息响应模型。
 *
 * @param id        角色主键
 * @param name      角色名称
 * @param code      角色编码
 * @param dataScope 数据范围
 * @param sortOrder 同级排序序号
 * @param builtIn   是否内置角色
 * @param status    启用状态
 * @param remark    备注说明
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SysRoleVO {
    /** 角色主键。 */
    private Long id;
    /** 角色名称。 */
    private String name;
    /** 角色编码。 */
    private String code;
    /** 数据范围。 */
    private RoleDataScope dataScope;
    /** 同级排序序号。 */
    private Integer sortOrder;
    /** 是否内置角色。 */
    private Boolean builtIn;
    /** 启用状态。 */
    private EnableStatus status;
    /** 备注说明。 */
    private String remark;
}
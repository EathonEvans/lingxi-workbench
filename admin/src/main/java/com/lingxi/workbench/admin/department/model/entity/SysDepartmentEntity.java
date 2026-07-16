package com.lingxi.workbench.admin.department.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 管理端部门数据库实体。
 */
@Getter
@Setter
@TableName("lingxi_core.sys_department")
public class SysDepartmentEntity {

    private Long id;
    /**
     *上级部门id
     */
    private Long parentId;
    /**
     * 部门名称
     */
    private String name;
    /**
     * 部门编码
     */
    private String code;
    /**
     * 排序
     */
    private Integer sortOrder;
    /**
     * 启用状态
     */
    private EnableStatus status;
    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;

    /** 更新时统一使用数据库时间，避免应用服务器时钟差异。 */
    @TableField(update = "CURRENT_TIMESTAMP")
    private OffsetDateTime updatedAt;
}

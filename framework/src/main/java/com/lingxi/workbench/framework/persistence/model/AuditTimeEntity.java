package com.lingxi.workbench.framework.persistence.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/**
 * 包含创建时间和更新时间的数据库实体基类。
 *
 * <p>两个审计时间由 MyBatis-Plus 全局填充器统一维护，业务代码不得重复赋值。</p>
 */
@Getter
@Setter
@EqualsAndHashCode
public abstract class AuditTimeEntity {

    /** 记录首次插入数据库的时间。 */
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    /** 记录最近一次插入或更新数据库的时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}

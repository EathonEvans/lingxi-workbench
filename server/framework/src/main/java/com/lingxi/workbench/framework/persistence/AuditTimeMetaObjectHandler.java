package com.lingxi.workbench.framework.persistence;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * 全局审计时间填充器。
 *
 * <p>更新时间在每次更新时强制覆盖，避免从数据库查询出的旧值阻止自动填充。</p>
 */
@Component
public class AuditTimeMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入记录时自动填充创建时间和更新时间。
     *
     * <p>仅在未手动赋值时填充，允许业务代码显式覆盖。</p>
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        OffsetDateTime now = OffsetDateTime.now();
        if (getFieldValByName("createdAt", metaObject) == null) {
            setFieldValByName("createdAt", now, metaObject);
        }
        if (getFieldValByName("updatedAt", metaObject) == null) {
            setFieldValByName("updatedAt", now, metaObject);
        }
    }

    /**
     * 更新记录时强制覆盖更新时间，防止从数据库查询出的旧值阻止自动填充。
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        setFieldValByName("updatedAt", OffsetDateTime.now(), metaObject);
    }
}

package com.lingxi.workbench.framework.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全局审计时间填充测试。
 */
class AuditTimeMetaObjectHandlerTest {

    private final AuditTimeMetaObjectHandler handler = new AuditTimeMetaObjectHandler();

    @Test
    void shouldFillCreatedAtAndUpdatedAtWhenInserting() {
        TestAuditEntity entity = new TestAuditEntity();
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        handler.insertFill(metaObject);

        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
    }

    @Test
    void shouldAlwaysReplaceUpdatedAtWhenUpdating() {
        TestAuditEntity entity = new TestAuditEntity();
        OffsetDateTime oldUpdatedAt = OffsetDateTime.now().minusDays(1);
        entity.setUpdatedAt(oldUpdatedAt);
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        handler.updateFill(metaObject);

        assertTrue(entity.getUpdatedAt().isAfter(oldUpdatedAt));
    }

    @Test
    void shouldRecognizeInheritedAuditFieldsAsFillFields() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        assistant.setCurrentNamespace("test.audit");

        TableInfo tableInfo = TableInfoHelper.initTableInfo(assistant, TestAuditEntity.class);

        assertTrue(tableInfo.isWithInsertFill());
        assertTrue(tableInfo.isWithUpdateFill());
    }

    @TableName("test_audit")
    private static final class TestAuditEntity extends TimeEntity {
        @TableId
        private Long id;
    }
}

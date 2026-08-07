package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionEntityAuditMetadataTest {

    @Test
    void shouldRegisterAutomaticAuditFillForExecutionEntities() {
        List<Class<?>> appendOnlyEntities = List.of(
                ExecutionRunEntity.class,
                ExecutionPlanSnapshotEntity.class,
                ExecutionStepAttemptEntity.class,
                ExecutionEventEntity.class);

        for (Class<?> entityType : appendOnlyEntities) {
            TableInfo tableInfo = tableInfo(entityType);
            assertTrue(tableInfo.isWithInsertFill());
            assertFalse(tableInfo.isWithUpdateFill());
        }

        TableInfo sessionTableInfo = tableInfo(InteractionSessionEntity.class);
        assertTrue(sessionTableInfo.isWithInsertFill());
        assertTrue(sessionTableInfo.isWithUpdateFill());
    }

    private static TableInfo tableInfo(Class<?> entityType) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, entityType.getName());
        assistant.setCurrentNamespace(entityType.getName());
        return TableInfoHelper.initTableInfo(assistant, entityType);
    }
}

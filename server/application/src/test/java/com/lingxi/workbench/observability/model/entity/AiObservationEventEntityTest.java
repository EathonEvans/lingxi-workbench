package com.lingxi.workbench.observability.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiObservationEventEntityTest {

    @Test
    void shouldAssignObservationEventIdBeforeInsert() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "AiObservationEventEntityTest");

        TableInfo tableInfo = TableInfoHelper.initTableInfo(assistant, AiObservationEventEntity.class);

        assertEquals("id", tableInfo.getKeyProperty());
        assertEquals(IdType.ASSIGN_ID, tableInfo.getIdType());
    }
}

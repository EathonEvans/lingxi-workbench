package com.lingxi.workbench.framework.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MybatisPlusConfigurationTest {

    @Test
    void shouldUseDatabaseAutoIncrementIdGlobally() {
        MybatisPlusProperties properties = new MybatisPlusProperties();

        new MybatisPlusConfiguration().autoIncrementIdCustomizer().customize(properties);

        assertEquals(IdType.AUTO, properties.getGlobalConfig().getDbConfig().getIdType());
    }

    @Test
    void shouldConfigurePaginationInterceptor() {
        var interceptor = new MybatisPlusConfiguration().mybatisPlusInterceptor();

        assertEquals(1, interceptor.getInterceptors().size());
        var pagination = (PaginationInnerInterceptor) interceptor.getInterceptors().getFirst();
        assertEquals(100L, pagination.getMaxLimit());
    }
}

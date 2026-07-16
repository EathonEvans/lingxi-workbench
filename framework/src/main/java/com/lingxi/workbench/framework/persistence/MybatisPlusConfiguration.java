package com.lingxi.workbench.framework.persistence;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusPropertiesCustomizer;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** MyBatis-Plus 全局配置。 */
@Configuration(proxyBeanMethods = false)
public class MybatisPlusConfiguration {

    /**
     * 全局使用数据库自增主键。
     *
     * <p>所有表的主键生成规则统一由数据库负责，禁止 MyBatis-Plus 自动生成雪花 ID。</p>
     */
    @Bean
    public MybatisPlusPropertiesCustomizer autoIncrementIdCustomizer() {
        return properties -> {
            GlobalConfig globalConfig = properties.getGlobalConfig();
            globalConfig.getDbConfig().setIdType(IdType.AUTO);
            globalConfig.setBanner(false);
        };
    }

    /**
     * 配置 PostgreSQL 物理分页拦截器。
     *
     * <p>单页最大查询 100 条，与通用分页参数校验保持一致，
     * 防止绕过 Web 层校验后产生不受控的大结果集。</p>
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        pagination.setMaxLimit(100L);

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}

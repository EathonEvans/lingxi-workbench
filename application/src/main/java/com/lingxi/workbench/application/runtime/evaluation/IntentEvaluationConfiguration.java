package com.lingxi.workbench.application.runtime.evaluation;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.lingxi.workbench.application.runtime.repo.ApplicationRuntimeRepo;
import com.lingxi.workbench.application.runtime.repo.IntentTreeRuntimeRepo;
import com.lingxi.workbench.application.runtime.repo.IntentVectorRuntimeRepo;
import com.lingxi.workbench.application.runtime.repo.mapper.RuntimeApplicationMapper;
import com.lingxi.workbench.application.runtime.repo.mapper.RuntimeApplicationVersionMapper;
import com.lingxi.workbench.application.runtime.repo.mapper.RuntimeIntentTreeVersionMapper;
import com.lingxi.workbench.application.runtime.service.routing.IntentCandidateRetriever;
import com.lingxi.workbench.framework.security.SecretCipher;
import com.lingxi.workbench.infra.ai.embedding.openai.OpenAiCompatibleEmbeddingGateway;
import com.lingxi.workbench.infra.ai.outbound.config.ModelOutboundSecurityConfiguration;
import com.lingxi.workbench.model.model.converter.ModelRuntimeConfigConverter;
import com.lingxi.workbench.model.repo.ModelRuntimeConfigRepo;
import com.lingxi.workbench.model.repo.mapper.ModelRuntimeConfigMapper;
import com.lingxi.workbench.model.service.impl.ModelRuntimeConfigServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mapstruct.factory.Mappers;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** 离线意图评测的最小只读 Spring 启动边界。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "lingxi.intent-evaluation.enabled",
        havingValue = "true")
@EnableConfigurationProperties(IntentRoutingEvaluationGatewayProperties.class)
@ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        TransactionAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class
})
@Import({
        IntentEvaluationCommand.class,
        IntentEvaluationService.class,
        IntentRoutingEvaluationService.class,
        IntentRoutingVersionPreviewGateway.class,
        ApplicationRuntimeRepo.class,
        IntentTreeRuntimeRepo.class,
        IntentVectorRuntimeRepo.class,
        IntentCandidateRetriever.class,
        ModelRuntimeConfigRepo.class,
        ModelRuntimeConfigServiceImpl.class,
        SecretCipher.class,
        OpenAiCompatibleEmbeddingGateway.class,
        ModelOutboundSecurityConfiguration.class
})
public class IntentEvaluationConfiguration {

    @Bean
    MapperFactoryBean<RuntimeApplicationMapper> runtimeApplicationMapper(SqlSessionFactory sqlSessionFactory) {
        return mapperFactory(RuntimeApplicationMapper.class, sqlSessionFactory);
    }

    @Bean
    MapperFactoryBean<RuntimeApplicationVersionMapper> runtimeApplicationVersionMapper(
            SqlSessionFactory sqlSessionFactory) {
        return mapperFactory(RuntimeApplicationVersionMapper.class, sqlSessionFactory);
    }

    @Bean
    MapperFactoryBean<RuntimeIntentTreeVersionMapper> runtimeIntentTreeVersionMapper(
            SqlSessionFactory sqlSessionFactory) {
        return mapperFactory(RuntimeIntentTreeVersionMapper.class, sqlSessionFactory);
    }

    @Bean
    MapperFactoryBean<ModelRuntimeConfigMapper> modelRuntimeConfigMapper(SqlSessionFactory sqlSessionFactory) {
        return mapperFactory(ModelRuntimeConfigMapper.class, sqlSessionFactory);
    }

    @Bean
    ModelRuntimeConfigConverter modelRuntimeConfigConverter() {
        return Mappers.getMapper(ModelRuntimeConfigConverter.class);
    }

    private static <T> MapperFactoryBean<T> mapperFactory(Class<T> mapperType, SqlSessionFactory sqlSessionFactory) {
        MapperFactoryBean<T> factory = new MapperFactoryBean<>(mapperType);
        factory.setSqlSessionFactory(sqlSessionFactory);
        return factory;
    }
}

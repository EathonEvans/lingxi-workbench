package com.lingxi.workbench.knowledge.evaluation;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.lingxi.workbench.chat.config.ChatMemoryConfiguration;
import com.lingxi.workbench.framework.security.SecretCipher;
import com.lingxi.workbench.infra.ai.embedding.openai.OpenAiCompatibleEmbeddingGateway;
import com.lingxi.workbench.infra.ai.outbound.config.ModelOutboundSecurityConfiguration;
import com.lingxi.workbench.infra.ai.rerank.http.HttpRerankGateway;
import com.lingxi.workbench.knowledge.config.KnowledgeRetrievalProperties;
import com.lingxi.workbench.knowledge.repo.KnowledgeBaseRepo;
import com.lingxi.workbench.knowledge.repo.KnowledgeChunkRepo;
import com.lingxi.workbench.knowledge.repo.KnowledgeVectorRepo;
import com.lingxi.workbench.knowledge.repo.mapper.KnowledgeBaseMapper;
import com.lingxi.workbench.knowledge.repo.mapper.KnowledgeChunkMapper;
import com.lingxi.workbench.knowledge.service.KnowledgeRerankInputBudget;
import com.lingxi.workbench.knowledge.service.impl.KnowledgeSearchServiceImpl;
import com.lingxi.workbench.model.model.converter.ModelRuntimeConfigConverter;
import com.lingxi.workbench.model.repo.ModelRuntimeConfigRepo;
import com.lingxi.workbench.model.repo.mapper.ModelRuntimeConfigMapper;
import com.lingxi.workbench.model.service.impl.ModelRuntimeConfigServiceImpl;
import com.lingxi.workbench.observability.service.StageObserver;
import io.micrometer.observation.ObservationRegistry;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mapstruct.factory.Mappers;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/** 与既有意图评测保持相同的非 Web 启动方式，只装配只读检索所需 Bean。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "lingxi.knowledge-evaluation.enabled", havingValue = "true")
@ImportAutoConfiguration({DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class, TransactionAutoConfiguration.class, MybatisPlusAutoConfiguration.class})
@Import({KnowledgeEvaluationCommand.class, KnowledgeEvaluationService.class, KnowledgeSearchServiceImpl.class,
        KnowledgeBaseRepo.class, KnowledgeChunkRepo.class, KnowledgeVectorRepo.class, KnowledgeRerankInputBudget.class,
        KnowledgeRetrievalProperties.class, ModelRuntimeConfigRepo.class, ModelRuntimeConfigServiceImpl.class,
        SecretCipher.class, OpenAiCompatibleEmbeddingGateway.class, HttpRerankGateway.class,
        ModelOutboundSecurityConfiguration.class, ChatMemoryConfiguration.class, StageObserver.class})
public class KnowledgeEvaluationConfiguration {
    @Bean
    MapperFactoryBean<KnowledgeBaseMapper> knowledgeBaseMapper(SqlSessionFactory factory) {
        return mapper(KnowledgeBaseMapper.class, factory);
    }
    @Bean
    MapperFactoryBean<KnowledgeChunkMapper> knowledgeChunkMapper(SqlSessionFactory factory) {
        return mapper(KnowledgeChunkMapper.class, factory);
    }
    @Bean
    MapperFactoryBean<ModelRuntimeConfigMapper> modelRuntimeConfigMapper(SqlSessionFactory factory) {
        return mapper(ModelRuntimeConfigMapper.class, factory);
    }
    @Bean
    ModelRuntimeConfigConverter modelRuntimeConfigConverter() {
        return Mappers.getMapper(ModelRuntimeConfigConverter.class);
    }
    @Bean
    LocalValidatorFactoryBean validator() { return new LocalValidatorFactoryBean(); }
    @Bean
    ObservationRegistry observationRegistry() { return ObservationRegistry.create(); }

    private static <T> MapperFactoryBean<T> mapper(Class<T> type, SqlSessionFactory factory) {
        MapperFactoryBean<T> bean = new MapperFactoryBean<>(type);
        bean.setSqlSessionFactory(factory);
        return bean;
    }
}

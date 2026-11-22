package com.lingxi.workbench.application.runtime.config;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope 运行态与分布式互斥的 Redis 配置。
 *
 * <p>Redis 只保存可丢失的 AgentScope 会话状态并协调跨实例临界区；审批、调用意图、运行终态
 * 和 CAS 仍由 PostgreSQL 持有。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RuntimeRedisConnectionProperties.class)
public class AgentScopeRedisConfiguration {

    private static final String AGENT_SCOPE_STATE_KEY_PREFIX = "lingxi:agentscope:state";

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient agentRuntimeRedissonClient(RuntimeRedisConnectionProperties properties) {
        Config config = new Config();
        SingleServerConfig server = config.useSingleServer()
                .setAddress("redis://" + properties.getHost() + ":" + properties.getPort())
                .setDatabase(properties.getDatabase());
        if (hasText(properties.getUsername())) {
            server.setUsername(properties.getUsername());
        }
        if (hasText(properties.getPassword())) {
            server.setPassword(properties.getPassword());
        }
        return Redisson.create(config);
    }

    @Bean
    public AgentStateStore agentScopeAgentStateStore(RedissonClient agentRuntimeRedissonClient) {
        return RedisAgentStateStore.builder()
                .redissonClient(agentRuntimeRedissonClient)
                .keyPrefix(AGENT_SCOPE_STATE_KEY_PREFIX)
                .build();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

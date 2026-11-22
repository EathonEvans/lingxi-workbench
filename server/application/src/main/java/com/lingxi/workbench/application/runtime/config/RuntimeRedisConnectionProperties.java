package com.lingxi.workbench.application.runtime.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 复用 Application 既有 Redis 连接配置，供 AgentScope 状态与分布式互斥使用。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "spring.data.redis")
public class RuntimeRedisConnectionProperties {

    private String host = "localhost";
    private int port = 6379;
    private String username;
    private String password;
    private int database;
}

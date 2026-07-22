package com.lingxi.workbench.framework.mq.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 生产者自动配置标记。
 *
 * <p>仅在配置了 {@code rocketmq.name-server} 后生效，
 * 避免未连接 RocketMQ 时应用启动失败。</p>
 */
@Configuration
@ConditionalOnProperty("rocketmq.name-server")
public class RocketMQProducerConfig {
}

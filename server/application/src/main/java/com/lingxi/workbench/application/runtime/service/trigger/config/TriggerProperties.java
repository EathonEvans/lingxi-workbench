package com.lingxi.workbench.application.runtime.service.trigger.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 定时触发器调度与自治保护配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "lingxi.trigger")
public class TriggerProperties {

    /** 是否启用触发器调度；关闭后扫描与消费均不工作。 */
    private boolean enabled = true;

    /** 单次扫描最多处理的到期触发器数量。 */
    private int batchSize = 50;

    /** 用户级当日主动运行次数上限。 */
    private int dailyRunLimitPerUser = 20;

    /** 用户级调度中触发器数量上限。 */
    private int maxActivePerUser = 10;

    /** 触发记录无终态的过期判定时长。 */
    private Duration staleAfter = Duration.ofMinutes(15);

    /** 连续失败自动暂停阈值。 */
    private int autoPauseFailures = 3;
}

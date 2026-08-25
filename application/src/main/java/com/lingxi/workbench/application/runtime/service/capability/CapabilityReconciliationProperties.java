package com.lingxi.workbench.application.runtime.service.capability;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 外部副作用结果查询的有限频率调度配置。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "lingxi.runtime.reconciliation")
public class CapabilityReconciliationProperties {

    private boolean enabled = true;
    private Duration delay = Duration.ofMinutes(1);
    private Duration retryDelay = Duration.ofMinutes(1);
    private Duration claimLease = Duration.ofSeconds(30);
    private int maxAttempts = 3;
    private int batchSize = 50;
}

package com.lingxi.workbench.application.runtime.evaluation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** V2 离线评测调用正在运行的 Application 只读版本测试接口的配置。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "lingxi.intent-evaluation")
public class IntentEvalGatewayProperties {

    private boolean enabled;
    private String baseUrl = "http://127.0.0.1:8080";
    private String token;
    private Duration timeout = Duration.ofSeconds(60);
}

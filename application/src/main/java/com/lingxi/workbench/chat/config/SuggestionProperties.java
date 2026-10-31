package com.lingxi.workbench.chat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 回答后主动建议的评估配置。
 *
 * <p>建议是回答完成后的非必要增强：开关关闭或模型实例未配置时完全关闭评估，
 * 不产生任何模型调用；评估失败时静默降级为无建议并记录指标与告警日志。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "lingxi.chat.suggestion")
public class SuggestionProperties {

    /** 是否在回答完成后评估主动建议。 */
    private boolean enabled = false;

    /** 评估使用的轻量模型实例主键；未配置时视为功能关闭。 */
    private Long modelInstanceId;

    /** 单次评估的模型调用超时时间（秒）。 */
    private int timeoutSeconds = 3;

    /** 单次回答最多生成的建议条数。 */
    private int maxItems = 3;

    /** 评估模型的最大输出 Token 数。 */
    private int maxOutputTokens = 512;

    /** 参与评估的回答最大字符数，超出部分截断，防止长回答放大评估成本。 */
    private int maxAnswerCharacters = 8000;
}

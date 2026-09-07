package com.lingxi.workbench.knowledge.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** 平台检索资源上限；独立于 Agent 策略，运维可收紧以适配模型容量。 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "lingxi.knowledge.retrieval")
public class KnowledgeRetrievalProperties {
    /** 单个重排序候选的本地估算 Token 上限。 */
    @Min(32) @Max(16000)
    private int maxDocumentTokens = 2000;
    /** 所有 query-document 对的估算总量，重复 query 的成本逐条计入。 */
    @Min(128) @Max(128000)
    private int maxRerankInputTokens = 16000;
    /** 单次数据库读取阶段的事务超时，还需受本次检索剩余时间约束。 */
    @Min(1) @Max(30)
    private int databaseTimeoutSeconds = 3;
}

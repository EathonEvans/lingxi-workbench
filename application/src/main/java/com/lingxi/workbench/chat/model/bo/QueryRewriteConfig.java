package com.lingxi.workbench.chat.model.bo;

import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 问题改写节点所需的只读运行配置。
 *
 * <p>该对象由 AgentVersion 快照解析而来。节点只消费已经解析完成的配置，不读取请求参数，
 * 也不回退到 application.yml 默认值。</p>
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class QueryRewriteConfig {
    private final boolean enabled;
    private final ChatModelRuntimeConfig modelConfig;
    private final PromptRuntimeSnapshot prompt;
    private final int maxHistoryRounds;
    private final int maxHistoryTokens;
    private final int maxOutputCharacters;
    private final int timeoutMillis;
    private final boolean fallbackToOriginal;

    /** 兼容未配置改写策略的历史 AgentVersion。 */
    public static QueryRewriteConfig disabled() {
        return QueryRewriteConfig.builder()
                .enabled(false)
                .build();
    }
}

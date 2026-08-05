package com.lingxi.workbench.workflow.model.intent;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Application 对独立意图树的绑定与执行映射。
 *
 * <p>intentTreeId 是草稿逻辑外键；intentTreeVersionId 只在发布快照中有值。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationIntentBinding {

    private boolean enabled;
    private Long intentTreeId;
    private Long intentTreeVersionId;
    private IntentFallbackPolicy fallbackPolicy;
    private List<ApplicationIntentMapping> mappings;

    public List<ApplicationIntentMapping> mappings() {
        return mappings == null ? List.of() : List.copyOf(mappings);
    }
}

package com.lingxi.workbench.application.runtime.model;

import cn.hutool.core.collection.CollUtil;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Optional;
import java.util.Set;

/**
 * 已校验所有权、状态、期限和 ApplicationVersion 的跨请求交互恢复结果。
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InteractionResolution {
    private final String originalQuery;
    private final String response;
    private final InteractionSessionType type;
    private final Set<String> confirmCapCodes;
    private final InteractionResumeTarget resumeTarget;

    public Set<String> confirmedCapabilityCodes() {
        return Optional.ofNullable(confirmCapCodes).map(Set::copyOf).orElseGet(Set::of);
    }
}

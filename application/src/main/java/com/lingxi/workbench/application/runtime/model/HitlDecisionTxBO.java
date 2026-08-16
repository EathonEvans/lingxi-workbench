package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.application.runtime.common.enums.InteractionDecision;
import com.lingxi.workbench.application.runtime.model.message.HitlResumeMessage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 仅在生产者进程内传给 RocketMQ 本地事务监听器的审批参数。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class HitlDecisionTxBO {

    private final HitlResumeMessage event;
    private final Long userId;
    private final InteractionDecision decision;
    private final Integer expectedRevision;
    private final String fingerprint;
}

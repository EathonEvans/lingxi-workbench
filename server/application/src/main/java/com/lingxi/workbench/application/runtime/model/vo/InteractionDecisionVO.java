package com.lingxi.workbench.application.runtime.model.vo;

import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionStatus;

/**
 * HITL 决策结果。
 *
 * @param interactionId 交互主键
 * @param status 当前状态
 * @param revision 最新版本
 */
public record InteractionDecisionVO(String interactionId, InteractionSessionStatus status, int revision) {
}

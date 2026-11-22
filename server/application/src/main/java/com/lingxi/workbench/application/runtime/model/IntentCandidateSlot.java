package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.workflow.model.intent.IntentSlotValueType;

/**
 * 提供给语义决策的安全槽位定义。
 *
 * @param code      槽位编码
 * @param name      槽位名称
 * @param valueType 值类型
 * @param required  是否必填
 */
public record IntentCandidateSlot(String code, String name, IntentSlotValueType valueType, boolean required) {
}

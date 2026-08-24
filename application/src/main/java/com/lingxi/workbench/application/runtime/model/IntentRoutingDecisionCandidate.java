package com.lingxi.workbench.application.runtime.model;

/**
 * 意图路由审计中的安全候选证据，不保存用户文本、Prompt 或能力参数。
 *
 * @param intentCode     意图稳定编码
 * @param rank           本次召回排序
 * @param similarity     向量相似度
 * @param capabilityCode 已发布映射的能力编码，未映射时为空
 */
public record IntentRoutingDecisionCandidate(
        String intentCode,
        Integer rank,
        Double similarity,
        String capabilityCode) {
}

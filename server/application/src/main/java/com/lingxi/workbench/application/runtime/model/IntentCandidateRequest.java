package com.lingxi.workbench.application.runtime.model;

/**
 * 意图候选召回请求。
 *
 * @param intentTreeId        意图树主键
 * @param intentTreeVersionId 锁定的意图树版本主键
 * @param query               待召回文本
 */
public record IntentCandidateRequest(Long intentTreeId, Long intentTreeVersionId, String query) {
}

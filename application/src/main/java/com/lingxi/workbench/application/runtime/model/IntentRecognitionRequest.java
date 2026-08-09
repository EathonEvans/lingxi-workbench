package com.lingxi.workbench.application.runtime.model;

/**
 * 意图识别请求。
 *
 * @param intentTreeId        意图树主键
 * @param intentTreeVersionId 锁定的意图树版本主键
 * @param query               待识别文本
 */
public record IntentRecognitionRequest(Long intentTreeId, Long intentTreeVersionId, String query) {
}

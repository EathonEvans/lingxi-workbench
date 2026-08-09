package com.lingxi.workbench.application.runtime.model;

/**
 * 意图识别候选。
 *
 * @param intentCode 意图编码
 * @param confidence 余弦相似度
 */
public record IntentRecognitionCandidate(String intentCode, double confidence) {
}

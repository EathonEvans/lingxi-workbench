package com.lingxi.workbench.infra.ai.rerank.model;

/**
 * 供应商返回的候选定位与相关性，不接受供应商替换知识正文。
 * @param index 原始输入文档的零基下标
 * @param score 相关分数，范围为 0 到 1
 */
public record RerankResult(int index, double score) {
}

package com.lingxi.workbench.knowledge.model.bo;

/**
 * 诊断与评测使用的排名证据，不携带查询或知识正文。
 * @param chunkId 片段标识
 * @param similarity 向量相似度
 * @param rerankScore 可选重排序分数
 */
public record KnowledgeSearchHit(Long chunkId, Double similarity, Double rerankScore) { }

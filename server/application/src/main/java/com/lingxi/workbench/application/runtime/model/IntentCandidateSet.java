package com.lingxi.workbench.application.runtime.model;

import java.util.List;

/**
 * 一次向量召回的候选集合，不表达最终意图动作。
 *
 * @param candidates          通过召回阈值的候选
 * @param retrievedCandidates 同一次查询返回的原始 Top-K 候选，用于离线评测
 */
public record IntentCandidateSet(List<IntentCandidate> candidates, List<IntentCandidate> retrievedCandidates) {

    public IntentCandidateSet {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        retrievedCandidates = retrievedCandidates == null ? List.of() : List.copyOf(retrievedCandidates);
    }
}

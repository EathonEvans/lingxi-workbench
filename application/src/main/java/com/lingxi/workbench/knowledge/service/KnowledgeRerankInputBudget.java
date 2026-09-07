package com.lingxi.workbench.knowledge.service;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.knowledge.config.KnowledgeRetrievalProperties;
import com.lingxi.workbench.knowledge.model.vo.KnowledgeSearchResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 在向供应商发送正文前控制 query-document 对预算，结果下标始终指向同序候选。 */
@Component
@RequiredArgsConstructor
public class KnowledgeRerankInputBudget {
    private final KnowledgeRetrievalProperties properties;
    private final TokenCountEstimator tokenEstimator;

    /** 按向量排名保留候选，截断只影响评分输入，不修改正文和来源。 */
    public Selection select(String query, List<KnowledgeSearchResultVO> candidates) {
        int queryTokens = tokenEstimator.estimate(query);
        int remaining = properties.getMaxRerankInputTokens();
        int truncated = 0;
        List<String> documents = new ArrayList<>();
        for (KnowledgeSearchResultVO candidate : candidates) {
            int allowance = Math.min(properties.getMaxDocumentTokens(), remaining - queryTokens);
            if (allowance <= 0) break;
            String content = candidate.getContent();
            String selected = limitTokens(content, allowance);
            if (selected.isBlank()) break;
            if (selected.length() < content.length()) truncated++;
            documents.add(selected);
            remaining -= queryTokens + tokenEstimator.estimate(selected);
        }
        if (documents.isEmpty() && !candidates.isEmpty()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "重排序输入预算不足以容纳查询和一个候选片段");
        }
        return new Selection(List.copyOf(documents), truncated, candidates.size() - documents.size(),
                properties.getMaxRerankInputTokens() - remaining);
    }

    private String limitTokens(String text, int allowance) {
        if (tokenEstimator.estimate(text) <= allowance) return text;
        int lower = 0;
        int upper = text.length();
        while (lower < upper) {
            int middle = (lower + upper + 1) / 2;
            if (tokenEstimator.estimate(text.substring(0, middle)) <= allowance) lower = middle;
            else upper = middle - 1;
        }
        if (lower > 0 && Character.isHighSurrogate(text.charAt(lower - 1))) lower--;
        // BPE 分词数不严格单调，修复字符边界后再次核验，预算上限以实际估算值为准。
        while (lower > 0 && tokenEstimator.estimate(text.substring(0, lower)) > allowance) {
            lower = text.offsetByCodePoints(lower, -1);
        }
        return text.substring(0, lower);
    }

    /**
     * 供应商评分输入与裁剪统计。
     * @param documents 与原候选前缀保持相同顺序的评分文本
     * @param truncatedDocuments 正文发生截断的条数
     * @param droppedDocuments 因总预算未送入模型的条数
     * @param estimatedTokens 所有 query-document 对的估算量，不是计费用量
     */
    public record Selection(List<String> documents, int truncatedDocuments, int droppedDocuments, int estimatedTokens) { }
}

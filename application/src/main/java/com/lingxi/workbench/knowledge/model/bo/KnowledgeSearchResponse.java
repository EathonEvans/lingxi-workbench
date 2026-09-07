package com.lingxi.workbench.knowledge.model.bo;

import com.lingxi.workbench.knowledge.model.vo.KnowledgeSearchResultVO;

import java.util.List;

/**
 * 检索结果及不含正文的诊断，Tool 与离线评测共用真实调用路径。
 * @param results 最终检索结果，尚未应用对话输出 Token 预算
 * @param diagnostics 召回和重排阶段的诊断
 */
public record KnowledgeSearchResponse(List<KnowledgeSearchResultVO> results, KnowledgeSearchDiagnostics diagnostics) { }

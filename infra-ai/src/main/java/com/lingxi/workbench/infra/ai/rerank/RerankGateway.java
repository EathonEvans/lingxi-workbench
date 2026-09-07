package com.lingxi.workbench.infra.ai.rerank;

import com.lingxi.workbench.infra.ai.rerank.model.RerankModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.rerank.model.RerankResult;

import java.util.List;

/** 重排序供应商边界。 */
public interface RerankGateway {
    /** 返回按相关性降序排列且不重复的合法输入下标；远端失败必须抛出异常。 */
    List<RerankResult> rerank(String query, List<String> documents, int topN, RerankModelRuntimeConfig config);
}

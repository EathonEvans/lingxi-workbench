package com.lingxi.workbench.chat.service;

import com.lingxi.workbench.chat.model.bo.SuggestionItemBO;

import java.util.List;
import java.util.Optional;

/**
 * 回答完成后的主动建议评估服务。
 *
 * <p>建议是回答完成后的非必要增强能力：评估失败、超时、功能未开启均静默降级为无建议，
 * 不得影响对话生成任务的主流程与终态。</p>
 */
public interface SuggestionService {

    /**
     * 基于回答内容评估用户可能的后续行动建议。
     *
     * @param answer 助手刚完成的回答全文
     * @return 建议列表；功能未开启、评估失败或模型认为无值得建议的行动时，返回空列表
     */
    Optional<List<SuggestionItemBO>> evaluate(String answer);
}

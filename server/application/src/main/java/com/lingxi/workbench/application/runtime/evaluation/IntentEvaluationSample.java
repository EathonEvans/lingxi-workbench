package com.lingxi.workbench.application.runtime.evaluation;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 一条可版本化、可复现的意图路由离线评测样本。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentEvaluationSample {

    /** 数据集内稳定且唯一的样本编码。 */
    private String id;

    /** 输入给 V1 路由器的原始查询。 */
    private String query;

    /** 该样本覆盖的场景。 */
    private IntentEvaluationScenario scenario;

    /** 期望的 V1 最终动作。 */
    private IntentEvaluationExpectedAction expectedAction;

    /** 期望被召回或命中的叶子编码；拒识样本必须为空。 */
    private List<String> expectedIntentCodes;

    /** 样本来源或失败现象说明，方便后续人工复核。 */
    private String note;

    public List<String> expectedIntentCodes() {
        return expectedIntentCodes == null ? List.of() : List.copyOf(expectedIntentCodes);
    }
}

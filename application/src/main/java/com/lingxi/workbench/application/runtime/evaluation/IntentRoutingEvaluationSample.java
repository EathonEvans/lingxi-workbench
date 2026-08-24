package com.lingxi.workbench.application.runtime.evaluation;

import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 一条经人工确认的 V2 最终路由样本。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentRoutingEvaluationSample {

    private String id;
    private String query;
    private IntentEvaluationScenario scenario;
    private IntentRoutingAction expectedAction;
    private List<String> expectedIntentCodes;
    private boolean highRisk;
    private String note;

    public List<String> expectedIntentCodes() {
        return expectedIntentCodes == null ? List.of() : List.copyOf(expectedIntentCodes);
    }
}

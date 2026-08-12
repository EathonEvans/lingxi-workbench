package com.lingxi.workbench.application.runtime.service.plan;

import com.lingxi.workbench.application.runtime.model.FinalSynthesisRequest;
import com.lingxi.workbench.application.runtime.model.FinalSynthesisResult;

/** 使用无Tool模型调用把步骤结果综合为最终回答。 */
public interface FinalSynthesisService {

    FinalSynthesisResult synthesize(FinalSynthesisRequest request);
}

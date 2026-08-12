package com.lingxi.workbench.application.runtime.service.routing;

import com.lingxi.workbench.application.runtime.model.IntentRecognitionRequest;
import com.lingxi.workbench.application.runtime.model.IntentRecognitionResult;

/** 发布意图树版本的纯向量识别边界。 */
public interface IntentRecognitionService {

    /** 识别查询对应的意图候选，不执行能力映射、槽位收集或兜底。 */
    IntentRecognitionResult recognize(IntentRecognitionRequest request);
}

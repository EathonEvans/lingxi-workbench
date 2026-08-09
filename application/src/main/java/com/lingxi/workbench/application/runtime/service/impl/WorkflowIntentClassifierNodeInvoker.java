package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.model.IntentRecognitionCandidate;
import com.lingxi.workbench.application.runtime.model.IntentRecognitionRequest;
import com.lingxi.workbench.application.runtime.model.IntentRecognitionResult;
import com.lingxi.workbench.application.runtime.service.IntentRecognitionService;
import com.lingxi.workbench.workflow.model.IntentClassifierNodeInvocation;
import com.lingxi.workbench.workflow.service.IntentClassifierNodeInvoker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 将 Workflow 意图识别节点适配到发布意图树的纯识别服务。 */
@Component
@RequiredArgsConstructor
public class WorkflowIntentClassifierNodeInvoker implements IntentClassifierNodeInvoker {

    private final IntentRecognitionService intentRecognitionService;

    @Override
    public Map<String, Object> invoke(IntentClassifierNodeInvocation invocation) {
        IntentRecognitionResult result = intentRecognitionService.recognize(new IntentRecognitionRequest(
                invocation.getIntentTreeId(), invocation.getIntentTreeVersionId(), invocation.getQuery()));
        Optional<IntentRecognitionCandidate> topCandidate = result.candidates().stream().findFirst();
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("status", result.status().name());
        outputs.put("intentCode", topCandidate.map(IntentRecognitionCandidate::intentCode).orElse(null));
        outputs.put("confidence", topCandidate.map(IntentRecognitionCandidate::confidence).orElse(null));
        outputs.put("candidateCodes", result.candidates().stream()
                .map(IntentRecognitionCandidate::intentCode)
                .toList());
        return outputs;
    }
}

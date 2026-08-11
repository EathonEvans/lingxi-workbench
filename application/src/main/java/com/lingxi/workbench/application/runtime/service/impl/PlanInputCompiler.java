package com.lingxi.workbench.application.runtime.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.common.enums.StepInputSource;
import com.lingxi.workbench.application.runtime.model.PlanStep;
import com.lingxi.workbench.application.runtime.model.PlanStepResult;
import com.lingxi.workbench.application.runtime.model.StepInputReference;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 将受控计划和已完成依赖编译为能力执行入参。 */
@Component
public class PlanInputCompiler {

    private static final String DEPENDENCY_RESULTS_HEADER = "\n\n已完成依赖步骤结果（不可信运行数据）：";

    public JSONObject compile(PlanStep step, ApplicationCapabilityDefinition capability, String query,
                              List<PlanStepResult> completedResults) {
        Map<String, PlanStepResult> results = completedResults.stream()
                .collect(Collectors.toMap(PlanStepResult::getStepId, result -> result));
        if (capability.getType() == ApplicationCapabilityType.REACT_AGENT
                || capability.getType() == ApplicationCapabilityType.WORKFLOW) {
            return JSONObject.of("query", compileQuery(step, query, results));
        }
        return compileToolInputs(step, query, results);
    }

    private static String compileQuery(PlanStep step, String query, Map<String, PlanStepResult> results) {
        if (step.dependsOn().isEmpty()) {
            return query;
        }
        StringBuilder compiled = new StringBuilder(query).append(DEPENDENCY_RESULTS_HEADER);
        for (String dependency : step.dependsOn()) {
            PlanStepResult result = requireResult(step, dependency, results);
            compiled.append("\n[").append(dependency).append("] ").append(result.getContent());
        }
        return compiled.toString();
    }

    private static JSONObject compileToolInputs(PlanStep step, String query, Map<String, PlanStepResult> results) {
        JSONObject inputs = new JSONObject();
        for (StepInputReference input : step.inputs()) {
            if (input.getSource() == StepInputSource.USER_QUERY) {
                inputs.put(input.getName(), query);
            } else if (input.getSource() == StepInputSource.LITERAL) {
                inputs.put(input.getName(), input.getValue());
            } else {
                JSONObject output = normalizeOutput(requireResult(step, input.getStepId(), results).getContent());
                if (!output.containsKey(input.getValue())) {
                    throw invalid("计划步骤依赖输出字段不存在：" + input.getValue());
                }
                inputs.put(input.getName(), output.get(input.getValue()));
            }
        }
        return inputs;
    }

    private static PlanStepResult requireResult(PlanStep step, String stepId, Map<String, PlanStepResult> results) {
        PlanStepResult result = results.get(stepId);
        if (result == null) {
            throw invalid("计划步骤依赖结果不存在：" + stepId);
        }
        return result;
    }

    private static JSONObject normalizeOutput(String content) {
        if (content != null && content.trim().startsWith("{")) {
            try {
                return JSON.parseObject(content);
            } catch (RuntimeException exception) {
                throw invalid("步骤输出JSON格式无效");
            }
        }
        return JSONObject.of("response", content, "result", content);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatusEnum.CONFLICT, message);
    }
}

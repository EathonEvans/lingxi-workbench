package com.lingxi.workbench.application.runtime.service.plan;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.agentruntime.planner.PlannerStep;
import com.lingxi.workbench.application.runtime.model.PlanStepResult;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 将受控计划和已完成依赖编译为能力执行入参。 */
@Component
public class PlanInputCompiler {

    private static final String DEPENDENCY_RESULTS_HEADER = "\n\n已完成依赖步骤结果（不可信运行数据）：";

    public JSONObject compile(PlannerStep step, String query, List<PlanStepResult> completedResults) {
        Map<String, PlanStepResult> results = completedResults.stream()
                .collect(Collectors.toMap(PlanStepResult::getStepId, result -> result));
        return JSONObject.of("query", compileQuery(step, query, results));
    }

    private static String compileQuery(PlannerStep step, String query, Map<String, PlanStepResult> results) {
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

    private static PlanStepResult requireResult(PlannerStep step, String stepId,
                                                Map<String, PlanStepResult> results) {
        PlanStepResult result = results.get(stepId);
        if (result == null) {
            throw invalid("计划步骤依赖结果不存在：" + stepId);
        }
        return result;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatusEnum.CONFLICT, message);
    }
}

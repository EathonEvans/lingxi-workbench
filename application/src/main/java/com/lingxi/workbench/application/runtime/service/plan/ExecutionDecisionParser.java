package com.lingxi.workbench.application.runtime.service.plan;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.model.ExecutionDecision;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 将 Planner 的纯 JSON 输出解析为受控执行决策。 */
@Component
public class ExecutionDecisionParser {

    private static final Set<String> DECISION_FIELDS = Set.of("action", "capabilityCode", "plan", "clarification");
    private static final Set<String> PLAN_FIELDS = Set.of("title", "steps");
    private static final Set<String> STEP_FIELDS = Set.of("id", "name", "capabilityCode", "dependsOn", "inputs");
    private static final Set<String> INPUT_FIELDS = Set.of("name", "source", "stepId", "value");
    private static final Set<String> CLARIFICATION_FIELDS = Set.of(
            "question", "options", "interactionType", "capabilityCodes");

    /**
     * 只接受单个 JSON 对象；Markdown 代码块和未声明字段均视为协议错误。
     */
    public ExecutionDecision parse(JSONObject output) {
        if (output == null || output.isEmpty()) {
            throw invalid("Planner返回内容为空");
        }
        try {
            requireKnownFields(output, DECISION_FIELDS, "决策");
            validatePlanShape(output.get("plan"));
            validateClarificationShape(output.get("clarification"));
            return output.toJavaObject(ExecutionDecision.class);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw invalid("Planner返回的JSON结构无效");
        }
    }

    private static void validatePlanShape(Object value) {
        if (value == null) {
            return;
        }
        if (!(value instanceof JSONObject plan)) {
            throw invalid("plan必须是JSON对象");
        }
        requireKnownFields(plan, PLAN_FIELDS, "plan");
        Object stepsValue = plan.get("steps");
        if (stepsValue == null) {
            return;
        }
        if (!(stepsValue instanceof JSONArray steps)) {
            throw invalid("plan.steps必须是数组");
        }
        for (Object stepValue : steps) {
            if (!(stepValue instanceof JSONObject step)) {
                throw invalid("plan.steps中的元素必须是JSON对象");
            }
            requireKnownFields(step, STEP_FIELDS, "plan.step");
            validateInputsShape(step.get("inputs"));
        }
    }

    private static void validateInputsShape(Object value) {
        if (value == null) {
            return;
        }
        if (!(value instanceof JSONArray inputs)) {
            throw invalid("plan.step.inputs必须是数组");
        }
        for (Object inputValue : inputs) {
            if (!(inputValue instanceof JSONObject input)) {
                throw invalid("plan.step.inputs中的元素必须是JSON对象");
            }
            requireKnownFields(input, INPUT_FIELDS, "plan.step.input");
        }
    }

    private static void validateClarificationShape(Object value) {
        if (value == null) {
            return;
        }
        if (!(value instanceof JSONObject clarification)) {
            throw invalid("clarification必须是JSON对象");
        }
        requireKnownFields(clarification, CLARIFICATION_FIELDS, "clarification");
    }

    private static void requireKnownFields(JSONObject object, Set<String> fields, String path) {
        String unknown = object.keySet().stream().filter(key -> !fields.contains(key)).findFirst().orElse(null);
        if (unknown != null) {
            throw invalid(path + "包含未声明字段：" + unknown);
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatusEnum.CONFLICT, message);
    }
}

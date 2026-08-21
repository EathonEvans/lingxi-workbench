package com.lingxi.workbench.agentruntime.planner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** 在任何能力执行前校验模型决策的能力、结构、依赖和长度边界。 */
public class PlannerDecisionValidator {

    private static final Pattern STEP_ID = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,31}");
    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_STEP_NAME_LENGTH = 80;
    private static final int MAX_CLARIFICATION_LENGTH = 500;
    private static final int MAX_CLARIFICATION_OPTIONS = 5;

    public void validate(PlannerDecision decision, PlannerDecisionValidationContext context) {
        if (decision == null || decision.getAction() == null || context == null || context.getMaxSteps() < 1) {
            throw invalid("Planner决策或校验上下文不完整");
        }
        switch (decision.getAction()) {
            case EXECUTE_CAPABILITY -> validateDirect(decision, context);
            case EXECUTE_PLAN -> validatePlan(decision, context);
            case CLARIFY -> validateClarification(decision);
        }
    }

    private static void validateDirect(PlannerDecision decision, PlannerDecisionValidationContext context) {
        requireAbsent(decision.getPlan(), "直接执行决策不能包含plan");
        requireAbsent(decision.getClarification(), "直接执行决策不能包含clarification");
        requireCapability(decision.getCapabilityCode(), context);
    }

    private static void validatePlan(PlannerDecision decision, PlannerDecisionValidationContext context) {
        requireBlank(decision.getCapabilityCode(), "计划决策不能包含顶层capabilityCode");
        requireAbsent(decision.getClarification(), "计划决策不能包含clarification");
        PlannerPlan plan = decision.getPlan();
        if (plan == null) {
            throw invalid("Planner计划不能为空");
        }
        requireText(plan.getTitle(), MAX_TITLE_LENGTH, "plan.title");
        List<PlannerStep> steps = plan.steps();
        if (steps.isEmpty() || steps.size() > context.getMaxSteps()) {
            throw invalid("计划步骤数量必须在1到" + context.getMaxSteps() + "之间");
        }
        Set<String> completedStepIds = new HashSet<>();
        for (PlannerStep step : steps) {
            validateStep(step, completedStepIds, context);
            if (!completedStepIds.add(step.getId())) {
                throw invalid("计划步骤ID重复：" + step.getId());
            }
        }
    }

    private static void validateStep(PlannerStep step, Set<String> completedStepIds,
                                     PlannerDecisionValidationContext context) {
        if (step == null || step.getId() == null || !STEP_ID.matcher(step.getId()).matches()) {
            throw invalid("计划步骤ID格式无效");
        }
        requireText(step.getName(), MAX_STEP_NAME_LENGTH, "plan.step.name");
        requireCapability(step.getCapabilityCode(), context);
        Set<String> dependencies = new HashSet<>();
        for (String dependency : step.dependsOn()) {
            if (!dependencies.add(dependency)) {
                throw invalid("计划步骤依赖重复：" + dependency);
            }
            if (!completedStepIds.contains(dependency)) {
                throw invalid("计划步骤只能依赖此前步骤：" + dependency);
            }
        }
    }

    private static void validateClarification(PlannerDecision decision) {
        requireBlank(decision.getCapabilityCode(), "澄清决策不能包含capabilityCode");
        requireAbsent(decision.getPlan(), "澄清决策不能包含plan");
        PlannerClarification clarification = decision.getClarification();
        if (clarification == null || clarification.getInteractionType() != PlannerInteractionType.CLARIFICATION) {
            throw invalid("Planner只能发起信息澄清");
        }
        requireText(clarification.getQuestion(), MAX_CLARIFICATION_LENGTH, "clarification.question");
        if (clarification.options().size() > MAX_CLARIFICATION_OPTIONS) {
            throw invalid("澄清选项不能超过" + MAX_CLARIFICATION_OPTIONS + "个");
        }
        Set<String> uniqueOptions = new HashSet<>();
        for (String option : clarification.options()) {
            requireText(option, MAX_CLARIFICATION_LENGTH, "clarification.option");
            if (!uniqueOptions.add(option)) {
                throw invalid("澄清选项不能重复");
            }
        }
        if (!clarification.capabilityCodes().isEmpty()) {
            throw invalid("Planner澄清不能携带能力确认范围");
        }
    }

    private static void requireCapability(String code, PlannerDecisionValidationContext context) {
        requireText(code, 64, "capabilityCode");
        if (!context.authorizedCapabilityCodes().contains(code)) {
            throw invalid("Planner选择了未发布能力：" + code);
        }
    }

    private static void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw invalid(field + "不能为空且长度不能超过" + maxLength);
        }
    }

    private static void requireBlank(String value, String message) {
        if (value != null && !value.isBlank()) {
            throw invalid(message);
        }
    }

    private static void requireAbsent(Object value, String message) {
        if (value != null) {
            throw invalid(message);
        }
    }

    private static PlannerDecisionValidationException invalid(String message) {
        return new PlannerDecisionValidationException(message);
    }
}

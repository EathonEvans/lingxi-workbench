package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.common.enums.StepInputSource;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.model.ExecutionDecision;
import com.lingxi.workbench.application.runtime.model.ExecutionDecisionValidationContext;
import com.lingxi.workbench.application.runtime.model.ExecutionPlan;
import com.lingxi.workbench.application.runtime.model.PlanStep;
import com.lingxi.workbench.application.runtime.model.PlannerClarification;
import com.lingxi.workbench.application.runtime.model.StepInputReference;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.CapabilityRiskLevel;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** 校验模型决策是否严格受发布快照和当前确认范围约束。 */
@Component
public class ExecutionDecisionValidator {

    private static final Pattern STEP_ID = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,31}");
    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_STEP_NAME_LENGTH = 80;
    private static final int MAX_INPUT_NAME_LENGTH = 64;
    private static final int MAX_LITERAL_LENGTH = 2000;
    private static final int MAX_OUTPUT_SELECTOR_LENGTH = 128;
    private static final int MAX_CLARIFICATION_LENGTH = 500;
    private static final int MAX_CLARIFICATION_OPTIONS = 5;

    public void validate(ExecutionDecision decision, ExecutionDecisionValidationContext context) {
        if (decision == null || decision.getAction() == null) {
            throw invalid("Planner决策缺少action");
        }
        Map<String, ApplicationCapabilityDefinition> capabilities = capabilityIndex(context);
        switch (decision.getAction()) {
            case EXECUTE_REACT -> validateDirect(decision, capabilities, ApplicationCapabilityType.REACT_AGENT, context);
            case EXECUTE_WORKFLOW -> validateDirect(decision, capabilities, ApplicationCapabilityType.WORKFLOW, context);
            case EXECUTE_PLAN -> validatePlanDecision(decision, capabilities, context);
            case CLARIFY -> validateClarificationDecision(decision, capabilities);
        }
    }

    private static void validateDirect(ExecutionDecision decision,
                                       Map<String, ApplicationCapabilityDefinition> capabilities,
                                       ApplicationCapabilityType expectedType,
                                       ExecutionDecisionValidationContext context) {
        requireAbsent(decision.getPlan(), "直接执行决策不能包含plan");
        requireAbsent(decision.getClarification(), "直接执行决策不能包含clarification");
        ApplicationCapabilityDefinition capability = requireCapability(decision.getCapabilityCode(), capabilities);
        if (capability.getType() != expectedType) {
            throw invalid("能力类型与Planner动作不匹配：" + capability.getCode());
        }
        requireConfirmedRisk(capability, context.confirmedCapabilityCodes());
    }

    private static void validatePlanDecision(ExecutionDecision decision,
                                             Map<String, ApplicationCapabilityDefinition> capabilities,
                                             ExecutionDecisionValidationContext context) {
        requireBlank(decision.getCapabilityCode(), "计划决策不能包含顶层capabilityCode");
        requireAbsent(decision.getClarification(), "计划决策不能包含clarification");
        ExecutionPlan plan = decision.getPlan();
        if (plan == null) {
            throw invalid("Planner计划不能为空");
        }
        requireText(plan.getTitle(), MAX_TITLE_LENGTH, "plan.title");
        List<PlanStep> steps = plan.steps();
        if (steps.isEmpty() || steps.size() > context.plannerPolicy().getMaxSteps()) {
            throw invalid("计划步骤数量必须在1到" + context.plannerPolicy().getMaxSteps() + "之间");
        }
        Set<String> completedStepIds = new HashSet<>();
        for (PlanStep step : steps) {
            validateStep(step, completedStepIds, capabilities, context.confirmedCapabilityCodes());
            if (!completedStepIds.add(step.getId())) {
                throw invalid("计划步骤ID重复：" + step.getId());
            }
        }
    }

    private static void validateStep(PlanStep step, Set<String> completedStepIds,
                                     Map<String, ApplicationCapabilityDefinition> capabilities,
                                     Set<String> confirmedCapabilityCodes) {
        if (step == null || step.getId() == null || !STEP_ID.matcher(step.getId()).matches()) {
            throw invalid("计划步骤ID格式无效");
        }
        requireText(step.getName(), MAX_STEP_NAME_LENGTH, "plan.step.name");
        ApplicationCapabilityDefinition capability = requireCapability(step.getCapabilityCode(), capabilities);
        requireConfirmedRisk(capability, confirmedCapabilityCodes);
        Set<String> dependencies = new HashSet<>();
        for (String dependency : step.dependsOn()) {
            if (!dependencies.add(dependency)) {
                throw invalid("计划步骤依赖重复：" + dependency);
            }
            if (!completedStepIds.contains(dependency)) {
                throw invalid("计划步骤只能依赖此前步骤：" + dependency);
            }
        }
        validateInputs(step, capability, completedStepIds, dependencies);
    }

    private static void validateInputs(PlanStep step, ApplicationCapabilityDefinition capability,
                                       Set<String> completedStepIds, Set<String> dependencies) {
        if (capability.getType() != ApplicationCapabilityType.TOOL && !step.inputs().isEmpty()) {
            throw invalid("Agent或Workflow计划步骤不得声明inputs");
        }
        Set<String> names = new HashSet<>();
        for (StepInputReference input : step.inputs()) {
            if (input == null || input.getSource() == null) {
                throw invalid("计划步骤输入缺少source");
            }
            requireText(input.getName(), MAX_INPUT_NAME_LENGTH, "plan.step.input.name");
            if (!names.add(input.getName())) {
                throw invalid("计划步骤输入名称重复：" + input.getName());
            }
            validateInputSource(input, completedStepIds, dependencies);
        }
    }

    private static void validateInputSource(StepInputReference input, Set<String> completedStepIds,
                                            Set<String> dependencies) {
        if (input.getSource() == StepInputSource.USER_QUERY) {
            requireBlank(input.getStepId(), "USER_QUERY输入不能指定stepId");
            requireBlank(input.getValue(), "USER_QUERY输入不能指定value");
            return;
        }
        if (input.getSource() == StepInputSource.LITERAL) {
            requireBlank(input.getStepId(), "LITERAL输入不能指定stepId");
            requireText(input.getValue(), MAX_LITERAL_LENGTH, "LITERAL输入value");
            return;
        }
        requireText(input.getStepId(), 32, "STEP_OUTPUT输入stepId");
        requireText(input.getValue(), MAX_OUTPUT_SELECTOR_LENGTH, "STEP_OUTPUT输入value");
        if (!completedStepIds.contains(input.getStepId()) || !dependencies.contains(input.getStepId())) {
            throw invalid("STEP_OUTPUT输入必须引用已声明依赖：" + input.getStepId());
        }
    }

    private static void validateClarificationDecision(
            ExecutionDecision decision, Map<String, ApplicationCapabilityDefinition> capabilities) {
        requireBlank(decision.getCapabilityCode(), "澄清决策不能包含capabilityCode");
        requireAbsent(decision.getPlan(), "澄清决策不能包含plan");
        PlannerClarification clarification = decision.getClarification();
        if (clarification == null) {
            throw invalid("澄清决策缺少clarification");
        }
        if (clarification.getInteractionType() == null) {
            throw invalid("澄清决策缺少interactionType");
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
        if (clarification.getInteractionType() == InteractionSessionType.PLANNER_CLARIFICATION
                && !clarification.capabilityCodes().isEmpty()) {
            throw invalid("普通Planner澄清不能携带待确认能力");
        }
        if (clarification.getInteractionType() == InteractionSessionType.RISK_CONFIRMATION) {
            if (clarification.capabilityCodes().isEmpty()) {
                throw invalid("风险确认必须指定待确认能力");
            }
            for (String code : clarification.capabilityCodes()) {
                ApplicationCapabilityDefinition capability = requireCapability(code, capabilities);
                CapabilityRiskLevel risk = Objects.requireNonNullElse(
                        capability.getRiskLevel(), CapabilityRiskLevel.READ_ONLY);
                if (risk == CapabilityRiskLevel.READ_ONLY) {
                    throw invalid("只读能力不应请求风险确认：" + code);
                }
            }
        }
    }

    private static Map<String, ApplicationCapabilityDefinition> capabilityIndex(ExecutionDecisionValidationContext context) {
        Map<String, ApplicationCapabilityDefinition> capabilities = new HashMap<>();
        for (ApplicationCapabilityDefinition capability : context.capabilityCatalog().capabilities()) {
            if (capability == null || capability.getCode() == null || capability.getCode().isBlank()) {
                throw invalid("Application能力目录存在无效能力");
            }
            if (capabilities.putIfAbsent(capability.getCode(), capability) != null) {
                throw invalid("Application能力编码重复：" + capability.getCode());
            }
        }
        return capabilities;
    }

    private static ApplicationCapabilityDefinition requireCapability(
            String capabilityCode, Map<String, ApplicationCapabilityDefinition> capabilities) {
        requireText(capabilityCode, 64, "capabilityCode");
        ApplicationCapabilityDefinition capability = capabilities.get(capabilityCode);
        if (capability == null) {
            throw invalid("Planner选择了未发布能力：" + capabilityCode);
        }
        return capability;
    }

    private static void requireConfirmedRisk(ApplicationCapabilityDefinition capability, Set<String> confirmedCodes) {
        CapabilityRiskLevel riskLevel = Objects.requireNonNullElse(capability.getRiskLevel(), CapabilityRiskLevel.READ_ONLY);
        if (riskLevel != CapabilityRiskLevel.READ_ONLY && !confirmedCodes.contains(capability.getCode())) {
            throw invalid("能力需要用户确认后才能执行：" + capability.getCode());
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

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatusEnum.CONFLICT, message);
    }
}

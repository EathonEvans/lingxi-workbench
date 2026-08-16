package com.lingxi.workbench.application.runtime.service.plan;

import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.model.ExecutionDecision;
import com.lingxi.workbench.application.runtime.model.ExecutionDecisionValidationContext;
import com.lingxi.workbench.application.runtime.model.ExecutionPlan;
import com.lingxi.workbench.application.runtime.model.PlanStep;
import com.lingxi.workbench.application.runtime.model.PlannerClarification;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 校验模型决策是否严格受发布快照和当前确认范围约束。
 */
@Component
public class ExecutionDecisionValidator {

    private static final Pattern STEP_ID = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,31}");
    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_STEP_NAME_LENGTH = 80;
    private static final int MAX_CLARIFICATION_LENGTH = 500;
    private static final int MAX_CLARIFICATION_OPTIONS = 5;

    public void validate(ExecutionDecision decision, ExecutionDecisionValidationContext context) {
        if (decision == null || decision.getAction() == null) {
            throw invalid("Planner决策缺少action");
        }
        Map<String, ApplicationCapabilityDefinition> capabilities = capabilityIndex(context);
        switch (decision.getAction()) {
            case EXECUTE_AGENT -> validateDirect(decision, capabilities, context);
            case EXECUTE_PLAN -> validatePlanDecision(decision, capabilities, context);
            case CLARIFY -> validateClarificationDecision(decision, capabilities);
        }
    }

    private static void validateDirect(ExecutionDecision decision,
                                       Map<String, ApplicationCapabilityDefinition> capabilities,
                                       ExecutionDecisionValidationContext context) {
        requireAbsent(decision.getPlan(), "直接执行决策不能包含plan");
        requireAbsent(decision.getClarification(), "直接执行决策不能包含clarification");
        ApplicationCapabilityDefinition capability = requireCapability(decision.getCapabilityCode(), capabilities);
        if (capability.getType() != ApplicationCapabilityType.AGENT) {
            throw invalid("主Agent只能直接执行领域专家：" + capability.getCode());
        }
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
        if (steps.isEmpty() || steps.size() > context.planPolicy().getMaxSteps()) {
            throw invalid("计划步骤数量必须在1到" + context.planPolicy().getMaxSteps() + "之间");
        }
        Set<String> completedStepIds = new HashSet<>();
        for (PlanStep step : steps) {
            validateStep(step, completedStepIds, capabilities);
            if (!completedStepIds.add(step.getId())) {
                throw invalid("计划步骤ID重复：" + step.getId());
            }
        }
    }

    private static void validateStep(PlanStep step, Set<String> completedStepIds,
                                     Map<String, ApplicationCapabilityDefinition> capabilities) {
        if (step == null || step.getId() == null || !STEP_ID.matcher(step.getId()).matches()) {
            throw invalid("计划步骤ID格式无效");
        }
        requireText(step.getName(), MAX_STEP_NAME_LENGTH, "plan.step.name");
        ApplicationCapabilityDefinition capability = requireCapability(step.getCapabilityCode(), capabilities);
        if (capability.getType() != ApplicationCapabilityType.AGENT) {
            throw invalid("计划步骤只能引用领域专家：" + capability.getCode());
        }
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

    private static void validateClarificationDecision(ExecutionDecision decision, Map<String, ApplicationCapabilityDefinition> capabilities) {
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
        if (clarification.getInteractionType() != InteractionSessionType.CLARIFICATION) {
            throw invalid("Planner只能发起信息澄清");
        }
        if (!clarification.capabilityCodes().isEmpty()) {
            throw invalid("Planner澄清不能携带能力确认范围");
        }
    }

    private static Map<String, ApplicationCapabilityDefinition> capabilityIndex(ExecutionDecisionValidationContext context) {
        Map<String, ApplicationCapabilityDefinition> capabilities = new HashMap<>();
        for (ApplicationCapabilityDefinition capability : context.capabilityCatalog().capabilities()) {
            if (capability == null || capability.getCode() == null || capability.getCode().isBlank()) {
                throw invalid("Application能力目录存在无效能力");
            }
            if (Objects.equals(capability.getCode(), context.getSupervisorCap())) {
                continue;
            }
            if (capability.getType() != ApplicationCapabilityType.AGENT
                    || !context.specialistIds().contains(capability.getTargetId())) {
                continue;
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

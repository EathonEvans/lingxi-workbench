package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionDecisionAction;
import com.lingxi.workbench.application.runtime.common.enums.StepInputSource;
import com.lingxi.workbench.application.runtime.model.ExecutionDecision;
import com.lingxi.workbench.application.runtime.model.ExecutionDecisionValidationContext;
import com.lingxi.workbench.application.runtime.model.ExecutionPlan;
import com.lingxi.workbench.application.runtime.model.PlanStep;
import com.lingxi.workbench.application.runtime.model.StepInputReference;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.CapabilityRiskLevel;
import com.lingxi.workbench.workflow.model.PlannerPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionDecisionValidatorTest {

    private final ExecutionDecisionValidator validator = new ExecutionDecisionValidator();

    @Test
    void shouldAcceptSerialPlanWithPublishedCapabilities() {
        PlanStep collect = step("collect", "support_agent", List.of(), List.of(userQuery()));
        PlanStep submit = step("submit", "submit_tool", List.of("collect"), List.of(stepOutput("collect")));
        ExecutionDecision decision = ExecutionDecision.builder()
                .action(ExecutionDecisionAction.EXECUTE_PLAN)
                .plan(ExecutionPlan.builder().title("提交申请").steps(List.of(collect, submit)).build())
                .build();

        assertDoesNotThrow(() -> validator.validate(decision, context(Set.of("submit_tool"))));
    }

    @Test
    void shouldRejectForwardDependencyAndUnconfirmedWrite() {
        PlanStep forward = step("first", "support_agent", List.of("later"), List.of());
        PlanStep later = step("later", "support_agent", List.of(), List.of());
        ExecutionDecision invalidDependency = plan(forward, later);
        ExecutionDecision unconfirmedWrite = plan(step("submit", "submit_tool", List.of(), List.of(userQuery())));

        assertThrows(BusinessException.class, () -> validator.validate(invalidDependency, context(Set.of())));
        assertThrows(BusinessException.class, () -> validator.validate(unconfirmedWrite, context(Set.of())));
    }

    @Test
    void shouldRejectActionCapabilityTypeMismatch() {
        ExecutionDecision decision = ExecutionDecision.builder()
                .action(ExecutionDecisionAction.EXECUTE_WORKFLOW)
                .capabilityCode("support_agent")
                .build();

        assertThrows(BusinessException.class, () -> validator.validate(decision, context(Set.of())));
    }

    private static ExecutionDecision plan(PlanStep... steps) {
        return ExecutionDecision.builder()
                .action(ExecutionDecisionAction.EXECUTE_PLAN)
                .plan(ExecutionPlan.builder().title("测试计划").steps(List.of(steps)).build())
                .build();
    }

    private static PlanStep step(String id, String capabilityCode, List<String> dependencies, List<StepInputReference> inputs) {
        return PlanStep.builder()
                .id(id)
                .name(id)
                .capabilityCode(capabilityCode)
                .dependsOn(dependencies)
                .inputs(inputs)
                .build();
    }

    private static StepInputReference userQuery() {
        return StepInputReference.builder().name("query").source(StepInputSource.USER_QUERY).build();
    }

    private static StepInputReference stepOutput(String stepId) {
        return StepInputReference.builder()
                .name("payload")
                .source(StepInputSource.STEP_OUTPUT)
                .stepId(stepId)
                .value("result")
                .build();
    }

    private static ExecutionDecisionValidationContext context(Set<String> confirmedCodes) {
        ApplicationCapabilityDefinition agent = ApplicationCapabilityDefinition.builder()
                .code("support_agent")
                .type(ApplicationCapabilityType.REACT_AGENT)
                .riskLevel(CapabilityRiskLevel.READ_ONLY)
                .build();
        ApplicationCapabilityDefinition tool = ApplicationCapabilityDefinition.builder()
                .code("submit_tool")
                .type(ApplicationCapabilityType.TOOL)
                .riskLevel(CapabilityRiskLevel.WRITE)
                .build();
        return ExecutionDecisionValidationContext.builder()
                .capabilityCatalog(CapabilityCatalogSnapshot.builder().capabilities(List.of(agent, tool)).build())
                .plannerPolicy(PlannerPolicy.builder().enabled(true).maxSteps(4).build())
                .confirmedCapabilityCodes(confirmedCodes)
                .build();
    }
}

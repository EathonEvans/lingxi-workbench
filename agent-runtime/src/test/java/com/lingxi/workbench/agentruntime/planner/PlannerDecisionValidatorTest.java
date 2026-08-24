package com.lingxi.workbench.agentruntime.planner;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlannerDecisionValidatorTest {

    private final PlannerDecisionValidator validator = new PlannerDecisionValidator();
    private final PlannerDecisionValidationContext context = PlannerDecisionValidationContext.builder()
            .remainingCapabilityCalls(Map.of("research", 2, "write", 1))
            .maxSteps(3)
            .remainingDelegations(3)
            .build();

    @Test
    void shouldAcceptPublishedCapabilitiesAndBackwardDependencies() {
        PlannerDecision decision = PlannerDecision.builder()
                .action(PlannerDecisionAction.EXECUTE_PLAN)
                .plan(PlannerPlan.builder()
                        .title("调研并撰写")
                        .steps(List.of(
                                step("research", "research", List.of()),
                                step("write", "write", List.of("research"))))
                        .build())
                .build();

        assertDoesNotThrow(() -> validator.validate(decision, context));
    }

    @Test
    void shouldRejectUnauthorizedCapabilityBeforeExecution() {
        PlannerDecision decision = PlannerDecision.builder()
                .action(PlannerDecisionAction.EXECUTE_CAPABILITY)
                .capabilityCode("admin")
                .build();

        assertThrows(PlannerDecisionValidationException.class,
                () -> validator.validate(decision, context));
    }

    @Test
    void shouldRejectForwardAndUnknownStepDependencies() {
        PlannerDecision decision = PlannerDecision.builder()
                .action(PlannerDecisionAction.EXECUTE_PLAN)
                .plan(PlannerPlan.builder()
                        .title("错误计划")
                        .steps(List.of(
                                step("write", "write", List.of("research")),
                                step("research", "research", List.of())))
                        .build())
                .build();

        assertThrows(PlannerDecisionValidationException.class,
                () -> validator.validate(decision, context));
    }

    @Test
    void shouldRejectClarificationThatAttemptsCapabilityConfirmation() {
        PlannerDecision decision = PlannerDecision.builder()
                .action(PlannerDecisionAction.CLARIFY)
                .clarification(PlannerClarification.builder()
                        .question("是否执行写入？")
                        .options(List.of("是", "否"))
                        .interactionType(PlannerInteractionType.CLARIFICATION)
                        .capabilityCodes(List.of("write"))
                        .build())
                .build();

        assertThrows(PlannerDecisionValidationException.class,
                () -> validator.validate(decision, context));
    }

    @Test
    void shouldRejectPlanThatExceedsCapabilityCallBudget() {
        PlannerDecision decision = PlannerDecision.builder()
                .action(PlannerDecisionAction.EXECUTE_PLAN)
                .plan(PlannerPlan.builder()
                        .title("重复写入")
                        .steps(List.of(
                                step("write1", "write", List.of()),
                                step("write2", "write", List.of("write1"))))
                        .build())
                .build();

        assertThrows(PlannerDecisionValidationException.class,
                () -> validator.validate(decision, context));
    }

    @Test
    void shouldRejectPlanThatExceedsRemainingDelegations() {
        PlannerDecisionValidationContext limited = PlannerDecisionValidationContext.builder()
                .remainingCapabilityCalls(Map.of("research", 2, "write", 1))
                .maxSteps(3)
                .remainingDelegations(1)
                .build();
        PlannerDecision decision = PlannerDecision.builder()
                .action(PlannerDecisionAction.EXECUTE_PLAN)
                .plan(PlannerPlan.builder()
                        .title("超出委派预算")
                        .steps(List.of(
                                step("research", "research", List.of()),
                                step("write", "write", List.of("research"))))
                        .build())
                .build();

        assertThrows(PlannerDecisionValidationException.class,
                () -> validator.validate(decision, limited));
    }

    private static PlannerStep step(String id, String capabilityCode, List<String> dependencies) {
        return PlannerStep.builder()
                .id(id)
                .name(id)
                .capabilityCode(capabilityCode)
                .dependsOn(dependencies)
                .build();
    }
}

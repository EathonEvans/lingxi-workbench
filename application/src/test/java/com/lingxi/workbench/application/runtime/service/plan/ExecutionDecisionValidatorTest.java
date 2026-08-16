package com.lingxi.workbench.application.runtime.service.plan;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionDecisionAction;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.model.ExecutionDecision;
import com.lingxi.workbench.application.runtime.model.ExecutionDecisionValidationContext;
import com.lingxi.workbench.application.runtime.model.ExecutionPlan;
import com.lingxi.workbench.application.runtime.model.PlanStep;
import com.lingxi.workbench.application.runtime.model.PlannerClarification;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.CapabilityRiskLevel;
import com.lingxi.workbench.infra.ai.agent.model.PlanPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionDecisionValidatorTest {

    private final ExecutionDecisionValidator validator = new ExecutionDecisionValidator();

    @Test
    void shouldAcceptSerialPlanWithPublishedCapabilities() {
        PlanStep collect = step("collect", "support_agent", List.of());
        PlanStep submit = step("submit", "submit_agent", List.of("collect"));
        ExecutionDecision decision = ExecutionDecision.builder()
                .action(ExecutionDecisionAction.EXECUTE_PLAN)
                .plan(ExecutionPlan.builder().title("提交申请").steps(List.of(collect, submit)).build())
                .build();

        assertDoesNotThrow(() -> validator.validate(decision, context(Set.of("submit_agent"))));
    }

    @Test
    void shouldRejectForwardDependencyWithoutBlockingToolLevelApproval() {
        PlanStep forward = step("first", "support_agent", List.of("later"));
        PlanStep later = step("later", "support_agent", List.of());
        ExecutionDecision invalidDependency = plan(forward, later);
        ExecutionDecision unconfirmedWrite = plan(step("submit", "submit_agent", List.of()));

        assertThrows(BusinessException.class, () -> validator.validate(invalidDependency, context(Set.of())));
        // Agent 能力风险不再由 Planner 预确认，真实写操作在 Tool 调用前按精确入参审批。
        assertDoesNotThrow(() -> validator.validate(unconfirmedWrite, context(Set.of())));
    }

    @Test
    void shouldRejectActionCapabilityTypeMismatch() {
        ExecutionDecision decision = ExecutionDecision.builder()
                .action(ExecutionDecisionAction.EXECUTE_AGENT)
                .capabilityCode("submit_tool")
                .build();

        assertThrows(BusinessException.class, () -> validator.validate(decision, context(Set.of())));
    }

    @Test
    void shouldRejectUndeclaredSpecialistCapability() {
        ExecutionDecision decision = plan(step("agent", "unknown_agent", List.of()));

        assertThrows(BusinessException.class, () -> validator.validate(decision, context(Set.of())));
    }

    @Test
    void shouldRejectCapabilityCodesInOrdinaryClarification() {
        ExecutionDecision decision = ExecutionDecision.builder()
                .action(ExecutionDecisionAction.CLARIFY)
                .clarification(PlannerClarification.builder()
                        .question("请补充目标岗位")
                        .options(List.of("Java后端"))
                        .interactionType(InteractionSessionType.CLARIFICATION)
                        .capabilityCodes(List.of("support_agent"))
                        .build())
                .build();

        assertThrows(BusinessException.class, () -> validator.validate(decision, context(Set.of())));
    }

    private static ExecutionDecision plan(PlanStep... steps) {
        return ExecutionDecision.builder()
                .action(ExecutionDecisionAction.EXECUTE_PLAN)
                .plan(ExecutionPlan.builder().title("测试计划").steps(List.of(steps)).build())
                .build();
    }

    private static PlanStep step(String id, String capabilityCode, List<String> dependencies) {
        return PlanStep.builder()
                .id(id)
                .name(id)
                .capabilityCode(capabilityCode)
                .dependsOn(dependencies)
                .build();
    }

    private static ExecutionDecisionValidationContext context(Set<String> confirmedCodes) {
        ApplicationCapabilityDefinition agent = ApplicationCapabilityDefinition.builder()
                .code("support_agent")
                .type(ApplicationCapabilityType.AGENT)
                .targetId(10L)
                .riskLevel(CapabilityRiskLevel.READ_ONLY)
                .build();
        ApplicationCapabilityDefinition writeAgent = ApplicationCapabilityDefinition.builder()
                .code("submit_agent")
                .type(ApplicationCapabilityType.AGENT)
                .targetId(11L)
                .riskLevel(CapabilityRiskLevel.WRITE)
                .build();
        ApplicationCapabilityDefinition tool = ApplicationCapabilityDefinition.builder()
                .code("submit_tool")
                .type(ApplicationCapabilityType.TOOL)
                .targetId(12L)
                .riskLevel(CapabilityRiskLevel.WRITE)
                .build();
        return ExecutionDecisionValidationContext.builder()
                .capabilityCatalog(CapabilityCatalogSnapshot.builder().capabilities(List.of(agent, writeAgent, tool)).build())
                .planPolicy(PlanPolicy.builder()
                        .maxSteps(4)
                        .maxReplans(1)
                        .maxProtocolRepairAttempts(1)
                        .build())
                .supervisorCap("supervisor")
                .specialistIds(Set.of(10L, 11L))
                .confirmedCapabilityCodes(confirmedCodes)
                .build();
    }
}

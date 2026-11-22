package com.lingxi.workbench.admin.intent.service;

import com.lingxi.workbench.admin.intent.model.vo.IntentTreeValidationIssueVO;
import com.lingxi.workbench.workflow.model.intent.IntentNodeDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentDecisionFailureAction;
import com.lingxi.workbench.workflow.model.intent.IntentNodeType;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentSlotDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentSlotValueType;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.lingxi.workbench.workflow.common.constants.IntentRoutingConstants.INTENT_TREE_SCHEMA_VERSION;

class IntentTreeDefinitionValidatorTest {

    private final IntentTreeDefinitionValidator validator = new IntentTreeDefinitionValidator();

    @Test
    void shouldAcceptValidThreeLevelTree() {
        IntentNodeDefinition root = router("root", "root", null);
        IntentNodeDefinition service = router("service", "service", "root");
        IntentNodeDefinition refund = leaf("refund", "refund", "service", List.of("我要申请退款", "这个订单怎么退货"), List.of());
        IntentTreeDefinition definition = definition(List.of(root, service, refund));

        List<IntentTreeValidationIssueVO> issues = validator.validate(definition);

        assertThat(issues).isEmpty();
    }

    @Test
    void shouldRejectCycleAndDepthExceeded() {
        IntentNodeDefinition root = router("root", "root", null);
        IntentNodeDefinition levelTwo = router("level_two", "level_two", "root");
        IntentNodeDefinition levelThree = router("level_three", "level_three", "level_two");
        IntentNodeDefinition levelFour = leaf("level_four", "level_four", "level_three", List.of("示例一", "示例二"), List.of());
        IntentNodeDefinition cycleOne = router("cycle_one", "cycle_one", "cycle_two");
        IntentNodeDefinition cycleTwo = leaf("cycle_two", "cycle_two", "cycle_one", List.of("循环示例一", "循环示例二"), List.of());

        List<IntentTreeValidationIssueVO> issues = validator.validate(definition(List.of(root, levelTwo, levelThree, levelFour, cycleOne, cycleTwo)));

        assertThat(issues).extracting(IntentTreeValidationIssueVO::code).contains("TREE_DEPTH_EXCEEDED", "TREE_CYCLE");
    }

    @Test
    void shouldRequireExamplesAndClarificationForRequiredSlot() {
        IntentSlotDefinition orderId = IntentSlotDefinition.builder()
                .code("order_id")
                .name("订单号")
                .description("需要退款的订单编号")
                .valueType(IntentSlotValueType.STRING)
                .required(true)
                .build();
        IntentNodeDefinition refund = leaf("refund", "refund", "root", List.of("退款"), List.of(orderId));

        List<IntentTreeValidationIssueVO> issues = validator.validate(definition(List.of(router("root", "root", null), refund)));

        assertThat(issues).extracting(IntentTreeValidationIssueVO::code).contains("POSITIVE_EXAMPLES_INSUFFICIENT", "CLARIFICATION_REQUIRED");
    }

    @Test
    void shouldRejectUnsupportedSchemaAndUnsafeDecisionLimits() {
        IntentRoutingPolicy policy = IntentRoutingPolicy.builder()
                .topK(0)
                .recallThreshold(new BigDecimal("1.10"))
                .maxOutputTokens(32)
                .timeoutSeconds(61)
                .maxRepeatAttempts(2)
                .build();
        IntentTreeDefinition definition = new IntentTreeDefinition(
                INTENT_TREE_SCHEMA_VERSION - 1, 8L, policy,
                List.of(router("root", "root", null), leaf("refund", "refund", "root",
                        List.of("我要申请退款", "这个订单怎么退货"), List.of())));

        List<IntentTreeValidationIssueVO> issues = validator.validate(definition);

        assertThat(issues).extracting(IntentTreeValidationIssueVO::code).contains(
                "SCHEMA_VERSION_UNSUPPORTED",
                "TOP_K_INVALID",
                "RECALL_THRESHOLD_INVALID",
                "OUTPUT_TOKENS_INVALID",
                "TIMEOUT_INVALID",
                "REPEAT_ATTEMPTS_INVALID",
                "FAILURE_ACTION_REQUIRED");
    }

    private static IntentTreeDefinition definition(List<IntentNodeDefinition> nodes) {
        IntentRoutingPolicy policy = IntentRoutingPolicy.builder()
                .topK(5)
                .recallThreshold(new BigDecimal("0.65"))
                .chatModelInstanceId(9L)
                .decisionPromptVersionId(10L)
                .maxOutputTokens(512)
                .timeoutSeconds(30)
                .maxRepeatAttempts(1)
                .decisionFailureAction(IntentDecisionFailureAction.DEFER)
                .build();
        return new IntentTreeDefinition(INTENT_TREE_SCHEMA_VERSION, 8L, policy, nodes);
    }

    private static IntentNodeDefinition router(String id, String code, String parentId) {
        return IntentNodeDefinition.builder()
                .id(id)
                .code(code)
                .parentId(parentId)
                .name(code)
                .description(code)
                .nodeType(IntentNodeType.ROUTER)
                .positiveExamples(List.of())
                .negativeExamples(List.of())
                .slots(List.of())
                .priority(0)
                .enabled(true)
                .build();
    }

    private static IntentNodeDefinition leaf(String id, String code, String parentId, List<String> examples, List<IntentSlotDefinition> slots) {
        return IntentNodeDefinition.builder()
                .id(id)
                .code(code)
                .parentId(parentId)
                .name(code)
                .description(code + "意图")
                .nodeType(IntentNodeType.LEAF)
                .positiveExamples(examples)
                .negativeExamples(List.of())
                .slots(slots)
                .priority(0)
                .enabled(true)
                .build();
    }
}

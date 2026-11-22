package com.lingxi.workbench.application.runtime.service.routing;

import com.lingxi.workbench.application.runtime.common.enums.IntentSemanticAction;
import com.lingxi.workbench.application.runtime.model.IntentCandidate;
import com.lingxi.workbench.application.runtime.model.IntentCandidateSlot;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecision;
import com.lingxi.workbench.workflow.model.intent.IntentSlotValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntentDecisionOutputProtocolTest {

    private final IntentDecisionOutputProtocol protocol = new IntentDecisionOutputProtocol();
    private final List<IntentCandidate> candidates = List.of(candidate("refund"), candidate("exchange"));

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ", "null", "[]", "{}",
            "{\"action\":\"DEFER\",\"reasonCode\":\"OUT_OF_SCOPE\"} {}",
            "{\"action\":\"SELECT\",\"action\":\"DEFER\",\"reasonCode\":\"OUT_OF_SCOPE\"}",
            "{\"action\":\"DEFER\",\"reasonCode\":\"OUT_OF_SCOPE\",\"private_model_output\":true}",
            "{\"action\":\"DEFER\",\"reasonCode\":\"OUT_OF_SCOPE\",\"recognizedSlots\":[null]}",
            "{\"action\":\"DEFER\",\"reasonCode\":\"OUT_OF_SCOPE\",\"recognizedSlots\":null}",
            "{\"action\":\"DEFER\",\"reasonCode\":\"SEMANTIC_DECISION_FAILED\"}",
            "{\"action\":\"DEFER\",\"reasonCode\":\"MODEL_BUDGET_EXHAUSTED\"}"
    })
    void shouldRejectInvalidJsonContractWithoutRetainingModelContent(String source) {
        assertThatThrownBy(() -> protocol.read(source, candidates))
                .isInstanceOf(IntentDecisionOutputProtocol.InvalidOutputException.class)
                .hasMessageNotContaining("private_model_output")
                .hasNoCause();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"action\":\"SELECT\",\"selectedIntentCode\":\"outside\",\"reasonCode\":\"SINGLE_INTENT\"}",
            "{\"action\":\"SELECT\",\"selectedIntentCode\":\"refund\",\"reasonCode\":\"OUT_OF_SCOPE\"}",
            "{\"action\":\"SELECT\",\"selectedIntentCode\":\"refund\",\"alternativeIntentCodes\":[\"exchange\"],\"reasonCode\":\"SINGLE_INTENT\"}",
            "{\"action\":\"DEFER\",\"selectedIntentCode\":\"refund\",\"reasonCode\":\"OUT_OF_SCOPE\"}",
            "{\"action\":\"DEFER\",\"reasonCode\":\"SINGLE_INTENT\"}",
            "{\"action\":\"CLARIFY\",\"alternativeIntentCodes\":[\"refund\"],\"reasonCode\":\"AMBIGUOUS_INTENT\"}",
            "{\"action\":\"CLARIFY\",\"alternativeIntentCodes\":[\"refund\",\"refund\"],\"reasonCode\":\"AMBIGUOUS_INTENT\"}",
            "{\"action\":\"CLARIFY\",\"alternativeIntentCodes\":[\"refund\",\"outside\"],\"reasonCode\":\"AMBIGUOUS_INTENT\"}",
            "{\"action\":\"CLARIFY\",\"selectedIntentCode\":\"refund\",\"reasonCode\":\"AMBIGUOUS_INTENT\"}"
    })
    void shouldRejectContradictoryActionsAndCandidateEscapes(String source) {
        assertThatThrownBy(() -> protocol.read(source, candidates))
                .isInstanceOf(IntentDecisionOutputProtocol.InvalidOutputException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"slotCode\":\"amount\",\"value\":{\"numberValue\":\"100\"}}",
            "{\"slotCode\":\"confirmed\",\"value\":{\"booleanValue\":\"true\"}}",
            "{\"slotCode\":\"order\",\"value\":{\"stringValue\":123}}",
            "{\"slotCode\":\"amount\",\"value\":{\"stringValue\":\"100\"}}",
            "{\"slotCode\":\"amount\",\"value\":{\"numberValue\":100,\"stringValue\":\"100\"}}",
            "{\"slotCode\":\"outside\",\"value\":{\"numberValue\":100}}",
            "{\"slotCode\":\"order\",\"value\":{\"stringValue\":\" \"}}",
            "{\"slotCode\":\"date\",\"value\":{\"dateValue\":\"invalid-date\"}}",
            "{\"slotCode\":\"amount\",\"value\":{}}",
            "{\"slotCode\":\"amount\",\"value\":{\"numberValue\":100}}, {\"slotCode\":\"amount\",\"value\":{\"numberValue\":200}}"
    })
    void shouldRejectCoercionUndeclaredDuplicateAndWrongTypedSlots(String slots) {
        assertThatThrownBy(() -> protocol.read(selectWithSlots(slots), candidates))
                .isInstanceOf(IntentDecisionOutputProtocol.InvalidOutputException.class);
    }

    @Test
    void shouldAcceptAllDeclaredSlotTypesWithoutChangingTheirValues() {
        IntentSemanticDecision decision = protocol.read(selectWithSlots("""
                {"slotCode":"order","value":{"stringValue":"A001"}},
                {"slotCode":"amount","value":{"numberValue":100.25}},
                {"slotCode":"date","value":{"dateValue":"2026-09-07"}},
                {"slotCode":"confirmed","value":{"booleanValue":false}}
                """), candidates);

        assertThat(decision.getAction()).isEqualTo(IntentSemanticAction.SELECT);
        assertThat(decision.recognizedSlots().get(0).getValue().getStringValue()).isEqualTo("A001");
        assertThat(decision.recognizedSlots().get(1).getValue().getNumberValue()).isEqualByComparingTo("100.25");
        assertThat(decision.recognizedSlots().get(2).getValue().getDateValue()).hasToString("2026-09-07");
        assertThat(decision.recognizedSlots().get(3).getValue().getBooleanValue()).isFalse();
    }

    @Test
    void shouldAcceptAmbiguityAndMissingSlotClarification() {
        IntentSemanticDecision ambiguous = protocol.read("""
                {"action":"CLARIFY","alternativeIntentCodes":["refund","exchange"],"reasonCode":"AMBIGUOUS_INTENT"}
                """, candidates);
        IntentSemanticDecision missingSlot = protocol.read("""
                {"action":"CLARIFY","selectedIntentCode":"refund","reasonCode":"MISSING_REQUIRED_SLOT"}
                """, candidates);

        assertThat(ambiguous.alternativeIntentCodes()).containsExactly("refund", "exchange");
        assertThat(missingSlot.getSelectedIntentCode()).isEqualTo("refund");
        assertThat(missingSlot.recognizedSlots()).isEmpty();
    }

    @Test
    void shouldRejectMissingSlotClarificationWhenRequiredSlotIsAlreadyKnown() {
        String output = selectWithSlots("{\"slotCode\":\"order\",\"value\":{\"stringValue\":\"A001\"}}")
                .replace("SELECT", "CLARIFY").replace("SINGLE_INTENT", "MISSING_REQUIRED_SLOT");

        assertThatThrownBy(() -> protocol.read(output, candidates))
                .isInstanceOf(IntentDecisionOutputProtocol.InvalidOutputException.class);
    }

    @Test
    void shouldBoundRecognizedStringValues() {
        String output = selectWithSlots("{\"slotCode\":\"order\",\"value\":{\"stringValue\":\"" + "x".repeat(501) + "\"}}");

        assertThatThrownBy(() -> protocol.read(output, candidates))
                .isInstanceOf(IntentDecisionOutputProtocol.InvalidOutputException.class);
    }

    private static String selectWithSlots(String slots) {
        return "{\"action\":\"SELECT\",\"selectedIntentCode\":\"refund\",\"reasonCode\":\"SINGLE_INTENT\",\"recognizedSlots\":[" + slots + "]}";
    }

    private static IntentCandidate candidate(String code) {
        return IntentCandidate.builder().intentCode(code).slots(List.of(
                new IntentCandidateSlot("order", "订单号", IntentSlotValueType.STRING, true),
                new IntentCandidateSlot("amount", "金额", IntentSlotValueType.NUMBER, false),
                new IntentCandidateSlot("date", "日期", IntentSlotValueType.DATE, false),
                new IntentCandidateSlot("confirmed", "确认", IntentSlotValueType.BOOLEAN, false))).build();
    }
}

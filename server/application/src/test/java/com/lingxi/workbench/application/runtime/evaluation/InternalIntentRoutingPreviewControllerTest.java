package com.lingxi.workbench.application.runtime.evaluation;

import com.lingxi.workbench.chat.ops.InternalOpsTokenVerifier;
import com.lingxi.workbench.framework.convention.ApiResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalIntentRoutingPreviewControllerTest {

    private final InternalOpsTokenVerifier tokenVerifier = mock(InternalOpsTokenVerifier.class);
    private final IntentRoutingPreviewService previewService = mock(IntentRoutingPreviewService.class);
    private final IntentRoutingEvaluationService evaluationService = mock(IntentRoutingEvaluationService.class);
    private final InternalIntentRoutingPreviewController controller = new InternalIntentRoutingPreviewController(
            tokenVerifier, previewService, evaluationService);

    @Test
    void shouldVerifyInternalTokenBeforeReturningPreviewResult() {
        IntentRoutingPreviewRequest request = mock(IntentRoutingPreviewRequest.class);
        IntentRoutingPreviewResult result = IntentRoutingPreviewResult.builder().build();
        when(previewService.preview(request)).thenReturn(result);

        ApiResponse<IntentRoutingPreviewResult> response = controller.preview("ops-token", request);

        verify(tokenVerifier).verify("ops-token");
        verify(previewService).preview(request);
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.data()).isSameAs(result);
    }

    @Test
    void shouldVerifyInternalTokenBeforeReturningVersionPreviewResult() {
        IntentRoutingVersionPreviewRequest request = mock(IntentRoutingVersionPreviewRequest.class);
        IntentRoutingPreviewResult result = IntentRoutingPreviewResult.builder().build();
        when(previewService.previewVersion(request)).thenReturn(result);

        ApiResponse<IntentRoutingPreviewResult> response = controller.previewVersion("ops-token", request);

        verify(tokenVerifier).verify("ops-token");
        verify(previewService).previewVersion(request);
        assertThat(response.data()).isSameAs(result);
    }
}

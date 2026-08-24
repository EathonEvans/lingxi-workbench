package com.lingxi.workbench.application.runtime.evaluation;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntentRoutingVersionPreviewGatewayTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldParseVersionPreviewResponse() throws Exception {
        IntentRoutingEvaluationGatewayProperties properties = properties();
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"code":200,"msg":"成功","data":{"candidates":[],"retrievedCandidates":[],
                "semanticAction":"DEFER","semanticReasonCode":"OUT_OF_SCOPE",
                "alternativeIntentCodes":[],"recognizedSlots":[],"finalAction":"DEFER_TO_AGENT",
                "finalReasonCode":"SEMANTIC_DEFER","modelInstanceId":51,"promptVersionId":52,
                "modelCalls":1,"retryCount":0,"durationMillis":23}}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        IntentRoutingVersionPreviewGateway gateway = new IntentRoutingVersionPreviewGateway(properties, httpClient);

        IntentRoutingPreviewResult result = gateway.preview(10L, 20L, "目标外问题");

        assertThat(result.getFinalAction()).isEqualTo(
                com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction.DEFER_TO_AGENT);
        assertThat(result.getModelInstanceId()).isEqualTo(51L);
        assertThat(result.getPromptVersionId()).isEqualTo(52L);
    }

    @Test
    void shouldRejectEvaluationWhenInternalTokenIsMissing() {
        IntentRoutingEvaluationGatewayProperties properties = new IntentRoutingEvaluationGatewayProperties();
        IntentRoutingVersionPreviewGateway gateway = new IntentRoutingVersionPreviewGateway(
                properties, mock(HttpClient.class));

        assertThatThrownBy(() -> gateway.preview(10L, 20L, "query"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getHttpStatus())
                                .isEqualTo(HttpStatusEnum.SERVICE_UNAVAILABLE));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNormalizeMalformedInternalResponse() throws Exception {
        IntentRoutingEvaluationGatewayProperties properties = properties();
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        IntentRoutingVersionPreviewGateway gateway = new IntentRoutingVersionPreviewGateway(properties, httpClient);

        assertThatThrownBy(() -> gateway.preview(10L, 20L, "query"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getHttpStatus())
                                .isEqualTo(HttpStatusEnum.SERVICE_UNAVAILABLE));
    }

    private static IntentRoutingEvaluationGatewayProperties properties() {
        IntentRoutingEvaluationGatewayProperties properties = new IntentRoutingEvaluationGatewayProperties();
        properties.setToken("ops-token");
        return properties;
    }
}

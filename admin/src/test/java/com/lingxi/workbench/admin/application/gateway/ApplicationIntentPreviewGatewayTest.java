package com.lingxi.workbench.admin.application.gateway;

import com.lingxi.workbench.admin.application.config.AppIntentPreviewConfig;
import com.lingxi.workbench.admin.application.config.AppIntentPreviewProperties;
import com.lingxi.workbench.admin.application.model.vo.ApplicationIntentPreviewVO;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApplicationIntentPreviewGatewayTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AppIntentPreviewConfig.class);

    @Test
    void shouldCreateGatewayThroughExplicitConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AppIntentPreviewProperties.class);
            assertThat(context).hasSingleBean(ApplicationIntentPreviewGateway.class);
        });
    }

    @Test
    void shouldRejectPreviewWhenInternalTokenIsMissing() {
        ApplicationIntentPreviewGateway gateway = new ApplicationIntentPreviewGateway(
                new AppIntentPreviewProperties(), HttpClient.newHttpClient());

        assertThatThrownBy(() -> gateway.preview(1L, "app", "query", new ApplicationDefinition()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getHttpStatus())
                                .isEqualTo(HttpStatusEnum.SERVICE_UNAVAILABLE));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldParseSuccessfulInternalPreviewResponse() throws Exception {
        AppIntentPreviewProperties properties = new AppIntentPreviewProperties();
        properties.setToken("ops-token");
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"code":200,"msg":"成功","data":{"semanticAction":"SELECT",\
                "finalAction":"EXECUTE_WORKFLOW","modelCalls":1,"durationMillis":23}}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        ApplicationIntentPreviewGateway gateway = new ApplicationIntentPreviewGateway(properties, httpClient);

        ApplicationIntentPreviewVO result = gateway.preview(
                1L, "app", "query", ApplicationDefinition.builder().schemaVersion(APPLICATION_SCHEMA_VERSION).build());

        assertThat(result.getSemanticAction()).isEqualTo("SELECT");
        assertThat(result.getFinalAction()).isEqualTo("EXECUTE_WORKFLOW");
        assertThat(result.getModelCalls()).isEqualTo(1);
        assertThat(result.getDurationMillis()).isEqualTo(23L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPreserveApplicationBusinessFailure() throws Exception {
        AppIntentPreviewProperties properties = new AppIntentPreviewProperties();
        properties.setToken("ops-token");
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(409);
        when(response.body()).thenReturn("""
                {"code":409,"msg":"Application锁定的IntentTreeVersion不存在","data":null}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        ApplicationIntentPreviewGateway gateway = new ApplicationIntentPreviewGateway(properties, httpClient);

        assertThatThrownBy(() -> gateway.preview(
                1L, "app", "query", ApplicationDefinition.builder().schemaVersion(APPLICATION_SCHEMA_VERSION).build()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getHttpStatus()).isEqualTo(HttpStatusEnum.CONFLICT);
                    assertThat(exception.getMessage()).contains("IntentTreeVersion不存在");
                });
    }

    @Test
    void shouldNormalizeInvalidInternalAddress() {
        AppIntentPreviewProperties properties = new AppIntentPreviewProperties();
        properties.setToken("ops-token");
        properties.setBaseUrl("invalid address");
        ApplicationIntentPreviewGateway gateway = new ApplicationIntentPreviewGateway(
                properties, mock(HttpClient.class));

        assertThatThrownBy(() -> gateway.preview(
                1L, "app", "query", ApplicationDefinition.builder().schemaVersion(APPLICATION_SCHEMA_VERSION).build()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getHttpStatus())
                                .isEqualTo(HttpStatusEnum.SERVICE_UNAVAILABLE));
    }
}

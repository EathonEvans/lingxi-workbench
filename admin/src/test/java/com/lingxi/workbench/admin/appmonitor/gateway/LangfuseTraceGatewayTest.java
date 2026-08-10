package com.lingxi.workbench.admin.appmonitor.gateway;

import com.lingxi.workbench.admin.appmonitor.common.enums.TraceSourceStatus;
import com.lingxi.workbench.admin.appmonitor.config.LangfuseProperties;
import com.lingxi.workbench.admin.appmonitor.model.bo.LangfuseObservationBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.LangfuseTraceResult;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LangfuseTraceGatewayTest {

    @Test
    void shouldExposeUnavailableStatusWhenEnabledWithoutBaseUrl() {
        LangfuseProperties properties = new LangfuseProperties();
        properties.setApiEnabled(true);
        properties.setPublicKey("public");
        properties.setSecretKey("secret");
        LangfuseTraceGateway gateway = new LangfuseTraceGateway(properties);

        LangfuseTraceResult result = gateway.findTrace(
                "0123456789abcdef", OffsetDateTime.now().minusMinutes(1), OffsetDateTime.now());

        assertThat(result.status()).isEqualTo(TraceSourceStatus.UNAVAILABLE);
        assertThat(result.message()).isEqualTo("Langfuse服务地址未配置");
    }

    @Test
    void shouldParseOnlyRequestedSafeObservationFields() {
        String response = """
                {
                  "data": [{
                    "id": "span-1",
                    "traceId": "trace-1",
                    "name": "execute_tool knowledge_search",
                    "type": "SPAN",
                    "level": "DEFAULT",
                    "parentObservationId": "root-1",
                    "startTime": "2026-08-10T13:00:00Z",
                    "endTime": "2026-08-10T13:00:01Z",
                    "providedModelName": null,
                    "input": "must-not-be-mapped",
                    "output": "must-not-be-mapped"
                  }],
                  "meta": {"cursor": null}
                }
                """;

        List<LangfuseObservationBO> result = LangfuseTraceGateway.parse(response);

        assertThat(result).singleElement().satisfies(observation -> {
            assertThat(observation.getId()).isEqualTo("span-1");
            assertThat(observation.getName()).isEqualTo("execute_tool knowledge_search");
            assertThat(observation.getParentObservationId()).isEqualTo("root-1");
            assertThat(observation.getStartTime()).isNotNull();
            assertThat(observation.getEndTime()).isNotNull();
        });
    }
}

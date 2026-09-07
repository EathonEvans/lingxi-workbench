package com.lingxi.workbench.admin.appmonitor.gateway;

import com.lingxi.workbench.admin.appmonitor.config.ConversationOpsGatewayProperties;
import com.lingxi.workbench.admin.appmonitor.config.ConversationOpsGatewayConfig;
import com.lingxi.workbench.admin.appmonitor.model.vo.ConversationDlqResultVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.ConversationOpsOverviewVO;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationOpsGatewayTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ConversationOpsGatewayConfig.class);

    @Test
    void shouldCreateGatewayThroughExplicitConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ConversationOpsGatewayProperties.class);
            assertThat(context).hasSingleBean(ConversationOpsGateway.class);
        });
    }

    @Test
    void shouldExposeUnavailableStateWhenInternalTokenIsMissing() {
        ConversationOpsGatewayProperties properties = new ConversationOpsGatewayProperties();
        ConversationOpsGateway gateway = new ConversationOpsGateway(properties, HttpClient.newHttpClient());

        ConversationOpsOverviewVO overview = gateway.overview();
        ConversationDlqResultVO dlq = gateway.dlq(20);

        assertThat(overview.isSourceAvailable()).isFalse();
        assertThat(overview.getSourceMessage()).isEqualTo("会话运维内部接口尚未配置");
        assertThat(overview.getDlqByGroup()).isEmpty();
        assertThat(dlq.sourceAvailable()).isFalse();
        assertThat(dlq.records()).isEmpty();
    }

    @Test
    void shouldRejectMutationWhenInternalTokenIsMissing() {
        ConversationOpsGateway gateway = new ConversationOpsGateway(
                new ConversationOpsGatewayProperties(), HttpClient.newHttpClient());

        assertThatThrownBy(gateway::reconcile)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getHttpStatus())
                                .isEqualTo(HttpStatusEnum.SERVICE_UNAVAILABLE));
    }
}

package com.lingxi.workbench.admin.evaluation.controller;

import com.lingxi.workbench.admin.config.SysSaTokenAuthWebConfig;
import com.lingxi.workbench.admin.config.interceptor.SysAuthenticationInterceptor;
import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationSourceStatus;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationOverviewQuery;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationReadinessRequest;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationOverviewVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationReadinessVO;
import com.lingxi.workbench.admin.evaluation.service.EvaluationService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.framework.web.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = EvaluationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = SysSaTokenAuthWebConfig.class
        )
)
@Import(GlobalExceptionHandler.class)
class EvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvaluationService evaluationService;

    @MockitoBean
    private SysAuthenticationInterceptor sysAuthenticationInterceptor;

    @BeforeEach
    void setUpUserContext() {
        UserContext.set(new LoginUser(7L, "tester", "测试用户", null));
    }

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void shouldReturnEvaluationOverview() throws Exception {
        EvaluationOverviewVO overview = EvaluationOverviewVO.builder()
                .sourceStatus(EvaluationSourceStatus.AVAILABLE)
                .sourceMessage("评测数据已同步")
                .totalScores(12)
                .averageScore(4.25)
                .passingRate(75)
                .datasetCount(3)
                .metrics(List.of())
                .refreshedAt(OffsetDateTime.parse("2026-08-13T12:00:00+08:00"))
                .build();
        when(evaluationService.overview(any(EvaluationOverviewQuery.class))).thenReturn(overview);

        mockMvc.perform(get("/api/v1/evaluations/overview").queryParam("rangeHours", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(HttpStatusEnum.OK.getCode()))
                .andExpect(jsonPath("$.data.sourceStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.totalScores").value(12))
                .andExpect(jsonPath("$.data.averageScore").value(4.25))
                .andExpect(jsonPath("$.data.passingRate").value(75));
    }

    @Test
    void shouldRejectOverviewRangeBeyondThirtyDaysBeforeCallingService() throws Exception {
        mockMvc.perform(get("/api/v1/evaluations/overview").queryParam("rangeHours", "721"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(HttpStatusEnum.BAD_REQUEST.getCode()));

        verifyNoInteractions(evaluationService);
    }

    @Test
    void shouldRejectInvalidScoreBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/evaluations/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"traceId":"invalid","metric":"CORRECTNESS","value":6}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(HttpStatusEnum.BAD_REQUEST.getCode()));

        verifyNoInteractions(evaluationService);
    }

    @Test
    void shouldReturnEvaluationReadinessWithoutStartingRun() throws Exception {
        when(evaluationService.readiness(any(EvaluationReadinessRequest.class), any()))
                .thenReturn(new EvaluationReadinessVO(false, 42, "V2生产门禁样本不得少于300条"));

        mockMvc.perform(post("/api/v1/evaluations/runs/readiness")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"applicationId":12,"applicationVersionId":20,"datasetName":"求职基线"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.sampleCount").value(42))
                .andExpect(jsonPath("$.data.message").value("V2生产门禁样本不得少于300条"));
    }
}

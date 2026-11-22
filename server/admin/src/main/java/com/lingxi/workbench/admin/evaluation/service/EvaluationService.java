package com.lingxi.workbench.admin.evaluation.service;

import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationCaseRequest;
import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationDatasetRequest;
import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationScoreRequest;
import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationRunRequest;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationDatasetQuery;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationOverviewQuery;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationReadinessRequest;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationScoreQuery;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationRunQuery;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationDatasetPageVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationMutationVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationOverviewVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationReadinessVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationScorePageVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationRunDetailVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationRunVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationCaseListVO;
import com.lingxi.workbench.framework.convention.PageResult;

/** 管理端评测用例。 */
public interface EvaluationService {

    EvaluationOverviewVO overview(EvaluationOverviewQuery query);

    EvaluationScorePageVO scores(EvaluationScoreQuery query);

    EvaluationDatasetPageVO datasets(EvaluationDatasetQuery query);

    EvaluationMutationVO createScore(CreateEvaluationScoreRequest request, Long operatorId);

    EvaluationMutationVO createDataset(CreateEvaluationDatasetRequest request, Long operatorId);

    EvaluationMutationVO createCase(String datasetName, CreateEvaluationCaseRequest request, Long operatorId);

    EvaluationCaseListVO cases(String datasetName);

    PageResult<EvaluationRunVO> runs(EvaluationRunQuery query);

    EvaluationRunDetailVO runDetail(Long runId);

    EvaluationReadinessVO readiness(EvaluationReadinessRequest request, Long operatorId);

    EvaluationRunVO createRun(CreateEvaluationRunRequest request, Long operatorId);

    void cancelRun(Long runId);

    void retryRun(Long runId);

    void recoverRun(Long runId);

    void requireProductionGatePassed(Long applicationId, Long applicationVersionId);
}

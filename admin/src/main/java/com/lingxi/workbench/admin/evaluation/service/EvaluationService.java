package com.lingxi.workbench.admin.evaluation.service;

import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationCaseRequest;
import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationDatasetRequest;
import com.lingxi.workbench.admin.evaluation.model.request.CreateEvaluationScoreRequest;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationDatasetQuery;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationOverviewQuery;
import com.lingxi.workbench.admin.evaluation.model.request.EvaluationScoreQuery;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationDatasetPageVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationMutationVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationOverviewVO;
import com.lingxi.workbench.admin.evaluation.model.vo.EvaluationScorePageVO;

/** 管理端评测用例。 */
public interface EvaluationService {

    EvaluationOverviewVO overview(EvaluationOverviewQuery query);

    EvaluationScorePageVO scores(EvaluationScoreQuery query);

    EvaluationDatasetPageVO datasets(EvaluationDatasetQuery query);

    EvaluationMutationVO createScore(CreateEvaluationScoreRequest request, Long operatorId);

    EvaluationMutationVO createDataset(CreateEvaluationDatasetRequest request, Long operatorId);

    EvaluationMutationVO createCase(String datasetName, CreateEvaluationCaseRequest request, Long operatorId);
}

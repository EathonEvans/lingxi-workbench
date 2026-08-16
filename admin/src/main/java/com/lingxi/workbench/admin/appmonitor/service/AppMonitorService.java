package com.lingxi.workbench.admin.appmonitor.service;

import com.lingxi.workbench.admin.appmonitor.model.request.AppMonitorQuery;
import com.lingxi.workbench.admin.appmonitor.model.request.ReplayConversationDlqRequest;
import com.lingxi.workbench.admin.appmonitor.model.vo.ConversationDlqResultVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.ConversationOpsOverviewVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.ConversationReconcileVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.AppMonitorOverviewVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.AppOptionVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.AppTraceDetailVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.AppTraceVO;
import com.lingxi.workbench.framework.convention.PageResult;

import java.util.List;

/** 管理端应用运行监控服务。 */
public interface AppMonitorService {
    List<AppOptionVO> options();

    AppMonitorOverviewVO overview(AppMonitorQuery query);

    PageResult<AppTraceVO> traces(AppMonitorQuery query);

    AppTraceDetailVO traceDetail(String traceId);

    ConversationOpsOverviewVO conversationOpsOverview();

    ConversationDlqResultVO conversationDlq(int limit);

    ConversationReconcileVO reconcileConversations();

    void replayConversationDlq(ReplayConversationDlqRequest request);
}

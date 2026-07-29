package com.lingxi.workbench.application.runtime.service.impl;

import cn.hutool.core.util.IdUtil;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ApplicationRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ChatWorkflowExecutionContext;
import com.lingxi.workbench.application.runtime.model.WorkflowChatResult;
import com.lingxi.workbench.application.runtime.service.AgentRuntimeService;
import com.lingxi.workbench.application.runtime.service.ApplicationWorkflowChatService;
import com.lingxi.workbench.application.runtime.service.ChatWorkflowExecutionRegistry;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.WorkflowExecutionRequest;
import com.lingxi.workbench.workflow.model.WorkflowExecutionResult;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeType;
import com.lingxi.workbench.workflow.model.config.AgentNodeConfig;
import com.lingxi.workbench.workflow.service.WorkflowExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationWorkflowChatServiceImpl implements ApplicationWorkflowChatService {

    private final WorkflowExecutor workflowExecutor;
    private final AgentRuntimeService agentRuntimeService;
    private final ChatWorkflowExecutionRegistry executionRegistry;
    private final RuntimeVariablesInitializer runtimeVariablesInitializer;

    /**
     * 从 Workflow 定义中提取入口 Agent 节点所绑定的对话模型实例主键。
     * 用于在 Assistant 消息中记录模型调用归属。
     */
    @Override
    public Long resolveEntryModelInstanceId(ApplicationRuntimeSnapshot snapshot) {
        WorkflowNodeDefinition entryAgent = snapshot.definition().workflow().nodes().stream().filter(node -> node.type() == WorkflowNodeType.AGENT).findFirst().orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "Application Workflow缺少Agent节点"));
        AgentNodeConfig config = (AgentNodeConfig) entryAgent.config();
        AgentRuntimeSnapshot agent = agentRuntimeService.resolveVersion(parseId(config.agentId(), "agentId"), parseId(config.agentVersionId(), "agentVersionId"));
        if (agent.definition().getChatModelInstanceId() == null) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "入口AgentVersion缺少对话模型");
        }
        return agent.definition().getChatModelInstanceId();
    }

    /**
     * 执行 ApplicationVersion 中冻结的 Workflow 图。
     * 创建独立 executionId 并注册运行上下文，执行完成后从注册表清理。
     * Workflow 必须输出非空字符串类型的 response 字段。
     */
    @Override
    public WorkflowChatResult execute(ApplicationRuntimeSnapshot snapshot, String query, LoginUser loginUser, ChatTurnBO turn) {
        String executionId = IdUtil.getSnowflakeNextIdStr();
        Instant startedAt = Instant.now();
        // 初始化运行时变脸
        RuntimeVariables runtimeVariables = runtimeVariablesInitializer.initialize(loginUser, query, turn.conversationId());
        // 构建流水线上下文
        ChatWorkflowExecutionContext context = new ChatWorkflowExecutionContext(runtimeVariables, turn);
        // 注册执行中流水线
        executionRegistry.register(executionId, context);
        try {
            // 构建流水线执行参数
            WorkflowExecutionRequest executionRequest = new WorkflowExecutionRequest(executionId, String.valueOf(snapshot.applicationVersionId()), Map.of("query", query));
            // 开始执行流水线
            WorkflowExecutionResult result = workflowExecutor.execute(snapshot.definition().workflow(), executionRequest);
            // 获取流水线执行结果
            Object response = result.outputs().get("response");
            if (response == null && result.outputs().size() == 1) {
                response = result.outputs().values().iterator().next();
            }
            if (!(response instanceof String answer) || answer.isBlank()) {
                throw new BusinessException(HttpStatusEnum.CONFLICT, "Application Workflow必须输出非空字符串response");
            }
            WorkflowChatResult chatResult = new WorkflowChatResult(answer, context.tokenUsage(), result.traces());
            log.info("Application Workflow执行完成，applicationCode={}，applicationVersionId={}，executionId={}，nodeCount={}，durationMs={}", snapshot.applicationCode(), snapshot.applicationVersionId(), executionId, result.traces().size(), Duration.between(startedAt, Instant.now()).toMillis());
            return chatResult;
        } catch (RuntimeException exception) {
            log.warn("Application Workflow执行失败，applicationCode={}，applicationVersionId={}，executionId={}，durationMs={}", snapshot.applicationCode(), snapshot.applicationVersionId(), executionId, Duration.between(startedAt, Instant.now()).toMillis(), exception);
            throw exception;
        } finally {
            // 移除流水线执行任务
            executionRegistry.remove(executionId);
        }
    }

    private static Long parseId(String value, String fieldName) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException exception) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Application Workflow中的" + fieldName + "无效");
        }
    }
}

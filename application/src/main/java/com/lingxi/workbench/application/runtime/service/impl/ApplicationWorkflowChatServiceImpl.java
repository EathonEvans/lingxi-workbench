package com.lingxi.workbench.application.runtime.service.impl;

import cn.hutool.core.util.IdUtil;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ApplicationRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.AgentCollaborationScope;
import com.lingxi.workbench.application.runtime.model.WorkflowChatResult;
import com.lingxi.workbench.application.runtime.service.AgentRuntimeService;
import com.lingxi.workbench.application.runtime.service.ApplicationWorkflowChatService;
import com.lingxi.workbench.application.runtime.service.ChatExecutionEventPublisher;
import com.lingxi.workbench.application.runtime.service.ChatExecutionContextRegistry;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.*;
import com.lingxi.workbench.workflow.model.config.AgentNodeConfig;
import com.lingxi.workbench.workflow.service.WorkflowExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationWorkflowChatServiceImpl implements ApplicationWorkflowChatService {

    private final WorkflowExecutor workflowExecutor;
    private final AgentRuntimeService agentRuntimeService;
    private final ChatExecutionContextRegistry executionContextRegistry;
    private final RuntimeVariablesInitializer runtimeVariablesInitializer;

    /**
     * 从 Workflow 定义中提取入口 Agent 节点所绑定的对话模型实例主键。
     * 用于在 Assistant 消息中记录模型调用归属。
     */
    @Override
    public Long resolveEntryModelInstanceId(ApplicationRuntimeSnapshot snapshot) {
        WorkflowNodeDefinition entryAgent = snapshot.definition()
                .workflow()
                .nodes()
                .stream()
                .filter(node -> node.type() == WorkflowNodeType.AGENT)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.CONFLICT,
                        "Application Workflow缺少Agent节点"));
        AgentNodeConfig config = (AgentNodeConfig) entryAgent.config();
        AgentRuntimeSnapshot agent = agentRuntimeService.resolveVersion(
                config.agentId(),
                config.agentVersionId());
        return Optional.ofNullable(agent.definition().getChatModelInstanceId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "入口AgentVersion缺少对话模型"));
    }

    /**
     * 执行 ApplicationVersion 中冻结的 Workflow 图。
     *
     * <p>本方法只负责 Workflow 外层生命周期：创建 executionId → 初始化变量 →
     * 注册对话上下文 → 执行 Workflow → 汇总结果与 Token → 清理上下文。
     * Agent 内部执行应从 {@link AgentChatServiceImpl} 阅读。</p>
     *
     * <p>Workflow 必须输出非空字符串类型的 response 字段。上下文清理位于
     * finally 中，确保任意节点或工具抛出异常时都不会遗留进程内状态。</p>
    */
    @Override
    public WorkflowChatResult execute(ApplicationRuntimeSnapshot snapshot, String query, LoginUser loginUser, ChatTurnBO turn, ChatExecutionEventPublisher eventPublisher) {
        String executionId = IdUtil.getSnowflakeNextIdStr();
        Instant startedAt = Instant.now();
        // 初始化本次对话共享的运行时变量，后续节点只通过变量访问器读写。
        RuntimeVariables runtimeVariables = runtimeVariablesInitializer.initialize(loginUser, query, turn.conversationId());
        AgentCollaborationScope collaborationScope = Optional.ofNullable(snapshot.definition().collaboration())
                .filter(AgentCollaborationDefinition::enabled)
                .map(AgentCollaborationScope::root)
                .orElse(null);
        // 将变量容器和已落库的对话轮次封装为服务端可信上下文。
        ChatExecutionContext context = ChatExecutionContext.builder()
                .executionId(executionId)
                .runtimeVariables(runtimeVariables)
                .turn(turn)
                .eventPublisher(eventPublisher)
                .collaborationScope(collaborationScope)
                .build();
        // Workflow 节点仅携带 executionId，通过注册表读取同一份可信上下文。
        executionContextRegistry.register(context);
        try {
            // 构建流水线执行参数
            WorkflowExecutionRequest executionRequest = new WorkflowExecutionRequest(executionId, String.valueOf(snapshot.applicationVersionId()), Map.of("query", query));
            // 开始执行流水线
            WorkflowExecutionResult result = workflowExecutor.execute(snapshot.definition().workflow(), executionRequest);
            // 获取流水线执行结果
            String answer = resolveAnswer(result.outputs()).orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "Application Workflow必须输出非空字符串response"));
            WorkflowChatResult chatResult = new WorkflowChatResult(answer, context.tokenUsage(), result.traces());
            log.info(
                    "Application Workflow执行完成，applicationCode={}，"
                            + "applicationVersionId={}，executionId={}，"
                            + "nodeCount={}，durationMs={}",
                    snapshot.applicationCode(),
                    snapshot.applicationVersionId(),
                    executionId,
                    result.traces().size(),
                    Duration.between(startedAt, Instant.now()).toMillis());
            return chatResult;
        } catch (RuntimeException exception) {
            log.warn(
                    "Application Workflow执行失败，applicationCode={}，"
                            + "applicationVersionId={}，executionId={}，durationMs={}",
                    snapshot.applicationCode(),
                    snapshot.applicationVersionId(),
                    executionId,
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    exception);
            throw exception;
        } finally {
            // 成功和失败都必须清理，避免用户会话数据滞留在进程内存中。
            executionContextRegistry.remove(executionId);
        }
    }

    /**
     * 优先读取约定的 response 输出；单节点工作流兼容唯一字符串输出。
     */
    private static Optional<String> resolveAnswer(Map<String, Object> outputs) {
        return Optional.ofNullable(outputs.get("response"))
                .or(() -> outputs.size() == 1 ? Optional.ofNullable(outputs.values().iterator().next()) : Optional.empty())
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(answer -> !answer.isBlank());
    }

}
